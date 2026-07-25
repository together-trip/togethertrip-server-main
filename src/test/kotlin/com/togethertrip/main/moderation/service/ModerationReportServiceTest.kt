package com.togethertrip.main.moderation.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.moderation.domain.ModerationReport
import com.togethertrip.main.moderation.domain.ModerationReportReason
import com.togethertrip.main.moderation.domain.ModerationReportStatus
import com.togethertrip.main.moderation.domain.ModerationTargetType
import com.togethertrip.main.moderation.dto.request.CreateModerationReportRequest
import com.togethertrip.main.moderation.exception.ModerationErrorCode
import com.togethertrip.main.moderation.repository.ModerationReportRepository
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.post.repository.PostCommentRepository
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.service.support.TripAccessResolver
import com.togethertrip.main.triprecap.repository.TripRecapRepository
import com.togethertrip.main.triprecap.domain.TripRecap
import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import org.springframework.dao.DataIntegrityViolationException

class ModerationReportServiceTest {
    private val access = mock(TripAccessResolver::class.java)
    private val users = mock(UserRepository::class.java)
    private val participants = mock(TripParticipantRepository::class.java)
    private val posts = mock(PostRepository::class.java)
    private val comments = mock(PostCommentRepository::class.java)
    private val recaps = mock(TripRecapRepository::class.java)
    private val reports = mock(ModerationReportRepository::class.java)
    private lateinit var service: ModerationReportService
    private lateinit var reporter: User
    private lateinit var target: User
    private lateinit var trip: Trip

    @BeforeEach
    fun setUp() {
        service = ModerationReportService(access, users, participants, posts, comments, recaps, reports)
        reporter = User("신고자").apply { id = 1 }
        target = User("대상").apply { id = 2 }
        trip = Trip(reporter, "여행", "KRW").apply { id = 10 }
        `when`(access.getActiveUser(1)).thenReturn(reporter)
        `when`(access.getAccessibleTrip(1, 10)).thenReturn(trip)
    }

    @Test
    fun `같은 여행 게시글 신고를 생성한다`() {
        val participant = TripParticipant(trip, target, target.nickname,
            participantRole = TripParticipantRole.MEMBER, participantStatus = TripParticipantStatus.ACTIVE)
        val post = Post(trip = trip, author = participant, postType = PostType.RECORD).apply { id = 20 }
        `when`(posts.findByIdAndTripIdAndDeletedAtIsNull(20, 10)).thenReturn(post)
        `when`(reports.existsByReporterIdAndTripIdAndTargetTypeAndTargetIdAndStatusInAndDeletedAtIsNull(
            1, 10, ModerationTargetType.POST, 20, ACTIVE)).thenReturn(false)
        `when`(reports.save(any(ModerationReport::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as ModerationReport).apply { id = 30 }
        }

        val response = service.create(1, 10, request(ModerationTargetType.POST, 20))
        assertEquals(30, response.id)
        assertEquals(2, response.targetUserId)
    }

    @Test
    fun `자기 게시글 신고를 거부한다`() {
        val participant = TripParticipant(trip, reporter, reporter.nickname,
            participantRole = TripParticipantRole.MEMBER, participantStatus = TripParticipantStatus.ACTIVE)
        `when`(posts.findByIdAndTripIdAndDeletedAtIsNull(20, 10))
            .thenReturn(Post(trip = trip, author = participant, postType = PostType.RECORD).apply { id = 20 })
        assertEquals(ModerationErrorCode.SELF_REPORT_NOT_ALLOWED,
            assertBusinessException { service.create(1, 10, request(ModerationTargetType.POST, 20)) }.errorCode)
        verify(reports, never()).save(any(ModerationReport::class.java))
    }

    @Test
    fun `처리 중인 중복 신고를 409 정책으로 거부한다`() {
        `when`(participants.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
            10, 2, TripParticipantStatus.ACTIVE)).thenReturn(true)
        `when`(users.findByIdAndDeletedAtIsNull(2)).thenReturn(target)
        `when`(reports.existsByReporterIdAndTripIdAndTargetTypeAndTargetIdAndStatusInAndDeletedAtIsNull(
            1, 10, ModerationTargetType.USER, 2, ACTIVE)).thenReturn(true)
        assertEquals(ModerationErrorCode.DUPLICATE_REPORT,
            assertBusinessException { service.create(1, 10, request(ModerationTargetType.USER, 2)) }.errorCode)
    }

    @Test
    fun `여행에 속하지 않은 사용자 ID 추측 신고를 거부한다`() {
        `when`(participants.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
            10, 2, TripParticipantStatus.ACTIVE)).thenReturn(false)
        assertEquals(ModerationErrorCode.TARGET_NOT_FOUND,
            assertBusinessException { service.create(1, 10, request(ModerationTargetType.USER, 2)) }.errorCode)
    }

    @Test
    fun `댓글 사용자 recap 신고 대상을 각각 같은 여행에서 해석한다`() {
        val participant = participant(target)
        val post = Post(trip = trip, author = participant, postType = PostType.RECORD).apply { id = 20 }
        val comment = PostComment(post, author = participant, content = "댓글").apply { id = 21 }
        `when`(comments.findByIdAndDeletedAtIsNull(21)).thenReturn(comment)
        `when`(participants.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
            10, 2, TripParticipantStatus.ACTIVE)).thenReturn(true)
        `when`(users.findByIdAndDeletedAtIsNull(2)).thenReturn(target)
        val recap = TripRecap(trip, reporter, TripRecapStyle.PHOTO).apply { id = 22 }
        `when`(recaps.findByIdAndDeletedAtIsNull(22)).thenReturn(recap)
        stubSave()

        assertEquals(2, service.create(1, 10, request(ModerationTargetType.COMMENT, 21)).targetUserId)
        assertEquals(2, service.create(1, 10, request(ModerationTargetType.USER, 2)).targetUserId)
        assertEquals(1, service.create(1, 10, request(ModerationTargetType.TRIP_RECAP, 22)).targetUserId)
    }

    @Test
    fun `필수 target type id reason 누락을 모두 거부한다`() {
        val requests = listOf(
            CreateModerationReportRequest(null, 1, ModerationReportReason.SPAM),
            CreateModerationReportRequest(ModerationTargetType.POST, null, ModerationReportReason.SPAM),
            CreateModerationReportRequest(ModerationTargetType.POST, 1, null),
        )
        requests.forEach { request ->
            assertEquals(ModerationErrorCode.TARGET_NOT_FOUND,
                assertBusinessException { service.create(1, 10, request) }.errorCode)
        }
    }

    @Test
    fun `DB unique 경합도 중복 신고 오류로 변환한다`() {
        `when`(participants.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
            10, 2, TripParticipantStatus.ACTIVE)).thenReturn(true)
        `when`(users.findByIdAndDeletedAtIsNull(2)).thenReturn(target)
        `when`(reports.save(any(ModerationReport::class.java)))
            .thenThrow(DataIntegrityViolationException("duplicate"))
        assertEquals(ModerationErrorCode.DUPLICATE_REPORT,
            assertBusinessException { service.create(1, 10, request(ModerationTargetType.USER, 2)) }.errorCode)
    }

    @Test
    fun `임시 참여자 작성 콘텐츠는 사용자 신고 대상으로 노출하지 않는다`() {
        val temporary = TripParticipant(
            trip, user = null, displayName = "임시", participantRole = TripParticipantRole.MEMBER,
            participantStatus = TripParticipantStatus.ACTIVE,
        )
        `when`(posts.findByIdAndTripIdAndDeletedAtIsNull(20, 10))
            .thenReturn(Post(trip = trip, author = temporary, postType = PostType.RECORD).apply { id = 20 })
        assertEquals(ModerationErrorCode.TARGET_NOT_FOUND,
            assertBusinessException { service.create(1, 10, request(ModerationTargetType.POST, 20)) }.errorCode)
    }

    private fun participant(user: User) = TripParticipant(
        trip, user, user.nickname, participantRole = TripParticipantRole.MEMBER,
        participantStatus = TripParticipantStatus.ACTIVE,
    )

    private fun stubSave() {
        `when`(reports.save(any(ModerationReport::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as ModerationReport).apply { id = targetId }
        }
    }

    private fun request(type: ModerationTargetType, id: Long) = CreateModerationReportRequest(
        targetType = type, targetId = id, reason = ModerationReportReason.SPAM, description = "검토 요청"
    )
    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try { block(); error("BusinessException expected") } catch (exception: BusinessException) { exception }
    }
    private companion object {
        val ACTIVE = listOf(ModerationReportStatus.PENDING, ModerationReportStatus.IN_REVIEW)
    }
}
