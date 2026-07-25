package com.togethertrip.main.moderation.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.moderation.exception.ModerationErrorCode
import com.togethertrip.main.moderation.repository.UserBlockRepository
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModerationPolicyTest {
    private val users = mock(UserRepository::class.java)
    private val blocks = mock(UserBlockRepository::class.java)
    private val filter = DefaultContentModerationFilter()
    private val policy = DefaultModerationPolicy(users, blocks, filter)

    @Test
    fun `NOOP 정책은 기존 서비스 단위 테스트와 호환된다`() {
        val post = post(PostType.RECORD)
        val participant = post.author
        val comment = com.togethertrip.main.post.domain.PostComment(post, author = participant, content = "댓글")
        ModerationPolicy.NOOP.validateUserCanWrite(1)
        ModerationPolicy.NOOP.validateContent("내용")
        ModerationPolicy.NOOP.validateCommentInteraction(1, post)
        assertTrue(ModerationPolicy.NOOP.canViewPost(1, post))
        assertTrue(ModerationPolicy.NOOP.canViewComment(1, comment))
        assertTrue(ModerationPolicy.NOOP.canNotify(1, 2))
        assertEquals(listOf(2L), ModerationPolicy.NOOP.filterNotifiableUserIds(1, listOf(2)))
    }

    @Test
    fun `금지 패턴이 포함된 내용은 원문을 노출하지 않고 거부한다`() {
        val exception = assertBusinessException { filter.validate("너를 죽여버린다") }
        assertEquals(ModerationErrorCode.CONTENT_REJECTED, exception.errorCode)
        assertFalse(exception.message.orEmpty().contains("죽여"))
    }

    @Test
    fun `정상 내용은 게시 전 필터를 통과한다`() {
        filter.validate("여행이 즐거웠어요", null)
    }

    @Test
    fun `제한된 사용자의 쓰기를 즉시 거부한다`() {
        val user = User("제한").apply { restrictModeration("위반", null, Instant.EPOCH) }
        `when`(users.findByIdAndDeletedAtIsNull(1)).thenReturn(user)
        assertEquals(ModerationErrorCode.USER_RESTRICTED, assertBusinessException { policy.validateUserCanWrite(1) }.errorCode)
    }

    @Test
    fun `양방향 차단 관계에서는 일반 기록을 숨긴다`() {
        val post = post(PostType.RECORD)
        `when`(blocks.existsBidirectionalBlock(2, 1)).thenReturn(true)
        assertFalse(policy.canViewPost(2, post))
    }

    @Test
    fun `차단 관계에서도 지출 기록은 보존하고 노출한다`() {
        val post = post(PostType.EXPENSE)
        `when`(blocks.existsBidirectionalBlock(2, 1)).thenReturn(true)
        assertTrue(policy.canViewPost(2, post))
    }

    @Test
    fun `차단 관계에서는 댓글 상호작용을 거부한다`() {
        val post = post(PostType.RECORD)
        `when`(blocks.existsBidirectionalBlock(2, 1)).thenReturn(true)
        assertEquals(ModerationErrorCode.INTERACTION_BLOCKED,
            assertBusinessException { policy.validateCommentInteraction(2, post) }.errorCode)
    }

    @Test
    fun `알림 수신자는 단일 batch 조회로 차단 관계를 제외한다`() {
        `when`(blocks.findNotBlockedRecipientUserIds(1, listOf(2L, 3L))).thenReturn(listOf(3L))
        assertEquals(listOf(3L), policy.filterNotifiableUserIds(1, listOf(2L, 3L)))
    }

    @Test
    fun `활성 사용자는 쓰기가 가능하고 없는 사용자는 거부한다`() {
        `when`(users.findByIdAndDeletedAtIsNull(1)).thenReturn(User("활성"))
        policy.validateUserCanWrite(1)
        assertEquals(ModerationErrorCode.TARGET_NOT_FOUND,
            assertBusinessException { policy.validateUserCanWrite(99) }.errorCode)
    }

    @Test
    fun `운영 숨김 기록은 차단 여부와 무관하게 보이지 않는다`() {
        val post = post(PostType.RECORD).apply { hideByModeration(Instant.EPOCH) }
        assertFalse(policy.canViewPost(2, post))
    }

    @Test
    fun `임시 참여자 기록은 사용자 차단 대상이 아니며 일반 기록은 미차단 시 보인다`() {
        val temporary = temporaryPost()
        assertTrue(policy.canViewPost(2, temporary))
        val regular = post(PostType.RECORD)
        `when`(blocks.existsBidirectionalBlock(2, 1)).thenReturn(false)
        assertTrue(policy.canViewPost(2, regular))
    }

    @Test
    fun `댓글은 운영 숨김과 양방향 차단을 각각 적용하고 임시 참여자는 허용한다`() {
        val regular = PostComment(post(PostType.RECORD), author = post(PostType.RECORD).author, content = "댓글")
        regular.hideByModeration(Instant.EPOCH)
        assertFalse(policy.canViewComment(2, regular))

        val blocked = PostComment(post(PostType.RECORD), author = post(PostType.RECORD).author, content = "댓글")
        `when`(blocks.existsBidirectionalBlock(2, 1)).thenReturn(true)
        assertFalse(policy.canViewComment(2, blocked))

        val temporaryPost = temporaryPost()
        val temporaryComment = PostComment(temporaryPost, author = temporaryPost.author, content = "임시")
        assertTrue(policy.canViewComment(2, temporaryComment))
    }

    @Test
    fun `미차단 또는 임시 작성자 게시글에는 댓글 상호작용할 수 있다`() {
        `when`(blocks.existsBidirectionalBlock(2, 1)).thenReturn(false)
        policy.validateCommentInteraction(2, post(PostType.RECORD))
        policy.validateCommentInteraction(2, temporaryPost())
    }

    @Test
    fun `단건 알림과 빈 batch 분기를 처리한다`() {
        `when`(blocks.existsBidirectionalBlock(1, 2)).thenReturn(false)
        assertTrue(policy.canNotify(1, 2))
        `when`(blocks.existsBidirectionalBlock(1, 2)).thenReturn(true)
        assertFalse(policy.canNotify(1, 2))
        assertEquals(emptyList(), policy.filterNotifiableUserIds(1, emptyList()))
    }

    @Test
    fun `미차단 일반 댓글은 노출한다`() {
        val post = post(PostType.RECORD)
        val comment = PostComment(post, author = post.author, content = "댓글")
        `when`(blocks.existsBidirectionalBlock(2, 1)).thenReturn(false)
        assertTrue(policy.canViewComment(2, comment))
    }

    private fun post(type: PostType): Post {
        val author = User("작성자").apply { id = 1 }
        val trip = Trip(author, "여행", "KRW").apply { id = 10 }
        val participant = TripParticipant(trip, author, author.nickname, participantRole = TripParticipantRole.MEMBER,
            participantStatus = TripParticipantStatus.ACTIVE).apply { id = 20 }
        return Post(trip = trip, author = participant, postType = type)
    }

    private fun temporaryPost(): Post {
        val owner = User("방장")
        val trip = Trip(owner, "여행", "KRW")
        val participant = TripParticipant(
            trip, user = null, displayName = "임시", participantRole = TripParticipantRole.MEMBER,
            participantStatus = TripParticipantStatus.ACTIVE,
        )
        return Post(trip = trip, author = participant, postType = PostType.RECORD)
    }

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try { block(); error("BusinessException expected") } catch (exception: BusinessException) { exception }
    }
}
