package com.togethertrip.main.moderation.domain

import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.triprecap.domain.TripRecap
import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.user.domain.User
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModerationDomainTest {
    private val now = Instant.parse("2026-07-25T00:00:00Z")

    @Test
    fun `사용자 제한 기간 중에는 제한 상태다`() {
        val user = user()
        user.restrictModeration("반복 위반", now.plusSeconds(60), now)
        assertTrue(user.isModerationRestricted(now.plusSeconds(1)))
        assertFalse(user.isModerationRestricted(now.plusSeconds(61)))
    }

    @Test
    fun `무기한 사용자 제한을 해제한다`() {
        val user = user()
        user.restrictModeration(null, null, now)
        assertTrue(user.isModerationRestricted(now.plusSeconds(999)))
        user.clearModerationRestriction(now.plusSeconds(1))
        assertFalse(user.isModerationRestricted(now.plusSeconds(2)))
        assertNull(user.moderationRestrictionReason)
    }

    @Test
    fun `일반 기록은 운영 숨김과 삭제가 가능하다`() {
        val post = post(PostType.RECORD)
        post.hideByModeration(now)
        assertFalse(post.isVisibleByModeration())
        post.deleteByModeration(now.plusSeconds(1))
        assertEquals(now.plusSeconds(1), post.moderationDeletedAt)
    }

    @Test
    fun `지출 기록은 운영 숨김으로 정산 근거를 훼손할 수 없다`() {
        val post = post(PostType.EXPENSE)
        assertFailsWith<IllegalArgumentException> { post.hideByModeration(now) }
        assertFailsWith<IllegalArgumentException> { post.deleteByModeration(now) }
        assertTrue(post.isVisibleByModeration())
    }

    @Test
    fun `댓글 운영 삭제는 일반 soft delete와 분리해 감사 대상을 보존한다`() {
        val comment = PostComment(post(PostType.RECORD), author = participant(), content = "댓글")
        comment.deleteByModeration(now)
        assertFalse(comment.isVisibleByModeration())
        assertNull(comment.deletedAt)
    }

    @Test
    fun `AI recap 운영 숨김 상태를 기록한다`() {
        val owner = user()
        val recap = TripRecap(trip(owner), owner, TripRecapStyle.PHOTO)
        recap.hideByModeration(now)
        assertFalse(recap.isVisibleByModeration())
    }

    @Test
    fun `신고 처리자는 상태와 처리 시각을 함께 기록한다`() {
        val reporter = user(1)
        val admin = user(2)
        val report = ModerationReport(
            trip = trip(reporter), reporter = reporter,
            targetType = ModerationTargetType.USER, targetId = 3,
            targetUser = user(3), reason = ModerationReportReason.SPAM,
        )
        report.handle(ModerationReportStatus.RESOLVED, admin, now)
        assertEquals(admin, report.handledBy)
        assertEquals(now, report.handledAt)
    }

    private fun user(id: Long = 1) = User("사용자$id").apply { this.id = id }
    private fun trip(owner: User = user()) = Trip(owner, "여행", "KRW").apply { id = 10 }
    private fun participant(): TripParticipant {
        val user = user()
        return TripParticipant(trip(user), user, user.nickname, participantRole = TripParticipantRole.MEMBER,
            participantStatus = TripParticipantStatus.ACTIVE).apply { id = 20 }
    }
    private fun post(type: PostType) = Post(trip = participant().trip, author = participant(), postType = type)
}
