package com.togethertrip.main.moderation.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.moderation.domain.ModerationAction
import com.togethertrip.main.moderation.domain.ModerationReport
import com.togethertrip.main.moderation.domain.ModerationReportAudit
import com.togethertrip.main.moderation.domain.ModerationReportReason
import com.togethertrip.main.moderation.domain.ModerationReportStatus
import com.togethertrip.main.moderation.domain.ModerationTargetType
import com.togethertrip.main.moderation.dto.request.HandleModerationReportRequest
import com.togethertrip.main.moderation.exception.ModerationErrorCode
import com.togethertrip.main.moderation.pagination.ModerationReportCursor
import com.togethertrip.main.moderation.repository.ModerationReportAuditRepository
import com.togethertrip.main.moderation.repository.ModerationReportRepository
import com.togethertrip.main.moderation.repository.ModerationReportSearchCondition
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.post.repository.PostCommentRepository
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.triprecap.repository.TripRecapRepository
import com.togethertrip.main.triprecap.domain.TripRecap
import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserRole
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import java.util.Optional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AdminModerationServiceTest {
    private val reports = mock(ModerationReportRepository::class.java)
    private val audits = mock(ModerationReportAuditRepository::class.java)
    private val users = mock(UserRepository::class.java)
    private val posts = mock(PostRepository::class.java)
    private val comments = mock(PostCommentRepository::class.java)
    private val recaps = mock(TripRecapRepository::class.java)
    private lateinit var service: AdminModerationService
    private lateinit var admin: User

    @BeforeEach
    fun setUp() {
        service = AdminModerationService(reports, audits, users, posts, comments, recaps)
        admin = User("운영자", role = UserRole.ADMIN).apply { id = 9 }
        `when`(users.findByIdAndDeletedAtIsNull(9)).thenReturn(admin)
    }

    @Test
    fun `일반 사용자의 서비스 직접 권한 우회를 거부한다`() {
        `when`(users.findByIdAndDeletedAtIsNull(1)).thenReturn(User("일반").apply { id = 1 })
        assertEquals(CommonErrorCode.ACCESS_DENIED,
            assertBusinessException { service.handle(1, 30, request(ModerationAction.NONE)) }.errorCode)
    }

    @Test
    fun `일반 기록을 숨기고 append only 감사를 저장한다`() {
        val fixture = fixture(PostType.RECORD)
        markInReview(fixture.report)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        `when`(posts.findById(20)).thenReturn(Optional.of(fixture.post))
        val result = service.handle(9, 30, request(ModerationAction.HIDE))
        assertEquals(ModerationReportStatus.RESOLVED, result.status)
        assertNotNull(fixture.post.moderationHiddenAt)
        assertEquals(1, mockingDetails(audits).invocations.count { it.method.name == "save" })
    }

    @Test
    fun `지출 기록에는 운영 삭제 조치를 적용하지 않는다`() {
        val fixture = fixture(PostType.EXPENSE)
        markInReview(fixture.report)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        `when`(posts.findById(20)).thenReturn(Optional.of(fixture.post))
        assertEquals(ModerationErrorCode.INVALID_ACTION,
            assertBusinessException { service.handle(9, 30, request(ModerationAction.DELETE)) }.errorCode)
    }

    @Test
    fun `사용자 제한과 해제를 도메인 상태로 반영한다`() {
        val fixture = fixture(PostType.RECORD)
        markInReview(fixture.report)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        service.handle(9, 30, request(ModerationAction.RESTRICT_USER, 7))
        assertNotNull(fixture.target.moderationRestrictedAt)
    }

    @Test
    fun `PENDING 신고는 NONE 조치로 IN_REVIEW만 전이한다`() {
        val fixture = fixture(PostType.RECORD)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        val result = service.handle(9, 30, request(ModerationAction.NONE, status = ModerationReportStatus.IN_REVIEW))
        assertEquals(ModerationReportStatus.IN_REVIEW, result.status)
    }

    @Test
    fun `PENDING에서 바로 RESOLVED 전이를 거부한다`() {
        val fixture = fixture(PostType.RECORD)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        assertEquals(ModerationErrorCode.INVALID_STATUS_TRANSITION,
            assertBusinessException { service.handle(9, 30, request(ModerationAction.NONE)) }.errorCode)
    }

    @Test
    fun `terminal 신고 재처리를 거부한다`() {
        val fixture = fixture(PostType.RECORD)
        fixture.report.handle(ModerationReportStatus.RESOLVED, admin, Instant.EPOCH)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        assertEquals(ModerationErrorCode.INVALID_STATUS_TRANSITION,
            assertBusinessException { service.handle(9, 30, request(ModerationAction.NONE)) }.errorCode)
    }

    @Test
    fun `REJECTED 전이는 NONE 조치만 허용한다`() {
        val fixture = fixture(PostType.RECORD)
        markInReview(fixture.report)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        assertEquals(ModerationErrorCode.INVALID_ACTION,
            assertBusinessException {
                service.handle(9, 30, request(ModerationAction.HIDE, status = ModerationReportStatus.REJECTED))
            }.errorCode)
    }

    @Test
    fun `USER 신고에는 콘텐츠 숨김 조치를 허용하지 않는다`() {
        val fixture = fixture(PostType.RECORD, ModerationTargetType.USER)
        markInReview(fixture.report)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        assertEquals(ModerationErrorCode.INVALID_ACTION,
            assertBusinessException { service.handle(9, 30, request(ModerationAction.HIDE)) }.errorCode)
    }

    @Test
    fun `IN_REVIEW 신고는 NONE 조치로 REJECTED 전이할 수 있다`() {
        val fixture = fixture(PostType.RECORD)
        markInReview(fixture.report)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        val response = service.handle(
            9, 30, request(ModerationAction.NONE, status = ModerationReportStatus.REJECTED)
        )
        assertEquals(ModerationReportStatus.REJECTED, response.status)
    }

    @Test
    fun `댓글과 recap 숨김 삭제 조치를 대상별로 적용한다`() {
        val fixture = fixture(PostType.RECORD)
        val comment = PostComment(fixture.post, author = fixture.post.author, content = "댓글").apply { id = 40 }
        val commentReport = report(fixture, ModerationTargetType.COMMENT, 40, 31)
        `when`(reports.findLockedById(31)).thenReturn(commentReport)
        `when`(comments.findByIdAndDeletedAtIsNull(40)).thenReturn(comment)
        service.handle(9, 31, request(ModerationAction.HIDE))
        assertNotNull(comment.moderationHiddenAt)

        val recap = TripRecap(fixture.post.trip, fixture.target, TripRecapStyle.PHOTO).apply { id = 50 }
        val recapReport = report(fixture, ModerationTargetType.TRIP_RECAP, 50, 32)
        `when`(reports.findLockedById(32)).thenReturn(recapReport)
        `when`(recaps.findByIdAndDeletedAtIsNull(50)).thenReturn(recap)
        service.handle(9, 32, request(ModerationAction.DELETE))
        assertNotNull(recap.moderationDeletedAt)
    }

    @Test
    fun `USER 신고 해결로 기존 사용자 제한을 해제한다`() {
        val fixture = fixture(PostType.RECORD, ModerationTargetType.USER)
        fixture.target.restrictModeration("기존", null, Instant.EPOCH)
        markInReview(fixture.report)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        service.handle(9, 30, request(ModerationAction.UNRESTRICT_USER))
        assertEquals(null, fixture.target.moderationRestrictedAt)
    }

    @Test
    fun `없는 관리자와 신고는 각각 거부한다`() {
        assertEquals(CommonErrorCode.ACCESS_DENIED,
            assertBusinessException { service.handle(404, 30, request(ModerationAction.NONE)) }.errorCode)
        assertEquals(ModerationErrorCode.REPORT_NOT_FOUND,
            assertBusinessException { service.handle(9, 404, request(ModerationAction.NONE)) }.errorCode)
    }

    @Test
    fun `처리 상태와 조치는 null을 허용하지 않는다`() {
        val fixture = fixture(PostType.RECORD)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        assertEquals(ModerationErrorCode.INVALID_ACTION,
            assertBusinessException {
                service.handle(9, 30, HandleModerationReportRequest(null, ModerationAction.NONE, "검토", null))
            }.errorCode)
        assertEquals(ModerationErrorCode.INVALID_ACTION,
            assertBusinessException {
                service.handle(9, 30, HandleModerationReportRequest(
                    ModerationReportStatus.IN_REVIEW, null, "검토", null
                ))
            }.errorCode)
    }

    @Test
    fun `게시글 삭제와 댓글 삭제 및 recap 숨김을 대상별로 적용한다`() {
        val fixture = fixture(PostType.RECORD)
        markInReview(fixture.report)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        `when`(posts.findById(20)).thenReturn(Optional.of(fixture.post))
        service.handle(9, 30, request(ModerationAction.DELETE))
        assertNotNull(fixture.post.moderationDeletedAt)

        val comment = PostComment(fixture.post, author = fixture.post.author, content = "댓글").apply { id = 41 }
        val commentReport = report(fixture, ModerationTargetType.COMMENT, 41, 31)
        `when`(reports.findLockedById(31)).thenReturn(commentReport)
        `when`(comments.findByIdAndDeletedAtIsNull(41)).thenReturn(comment)
        service.handle(9, 31, request(ModerationAction.DELETE))
        assertNotNull(comment.moderationDeletedAt)

        val recap = TripRecap(fixture.post.trip, fixture.target, TripRecapStyle.PHOTO).apply { id = 51 }
        val recapReport = report(fixture, ModerationTargetType.TRIP_RECAP, 51, 32)
        `when`(reports.findLockedById(32)).thenReturn(recapReport)
        `when`(recaps.findByIdAndDeletedAtIsNull(51)).thenReturn(recap)
        service.handle(9, 32, request(ModerationAction.HIDE))
        assertNotNull(recap.moderationHiddenAt)
    }

    @Test
    fun `해결 시 NONE 조치도 허용한다`() {
        val fixture = fixture(PostType.RECORD)
        markInReview(fixture.report)
        `when`(reports.findLockedById(30)).thenReturn(fixture.report)
        assertEquals(ModerationReportStatus.RESOLVED,
            service.handle(9, 30, request(ModerationAction.NONE)).status)
    }

    @Test
    fun `관리자 신고 목록은 생성 시각과 id 커서로 오래된 순서부터 조회한다`() {
        val first = fixture(PostType.RECORD).report.apply {
            id = 30
            createdAt = Instant.parse("2026-07-25T00:00:00Z")
        }
        val second = fixture(PostType.RECORD).report.apply {
            id = 31
            createdAt = Instant.parse("2026-07-25T00:00:01Z")
        }
        val extra = fixture(PostType.RECORD).report.apply {
            id = 32
            createdAt = Instant.parse("2026-07-25T00:00:02Z")
        }
        `when`(reports.findReports(
            ModerationReportSearchCondition(
                status = ModerationReportStatus.PENDING,
                targetType = ModerationTargetType.POST,
                cursor = null,
            ),
            3,
        )).thenReturn(listOf(first, second, extra))

        val result = service.getReports(
            9, ModerationReportStatus.PENDING, ModerationTargetType.POST, null, 2
        )

        assertEquals(listOf(30L, 31L), result.items.map { it.id })
        assertEquals(true, result.hasNext)
        assertEquals(2, result.size)
        assertEquals(
            ModerationReportCursor(second.createdAt, second.id),
            ModerationReportCursor.decode(requireNotNull(result.nextCursor)),
        )
    }

    @Test
    fun `관리자 신고 목록은 커서와 크기 상한을 저장소 조건으로 전달한다`() {
        val cursor = ModerationReportCursor(Instant.parse("2026-07-25T00:00:00Z"), 30)
        `when`(reports.findReports(
            ModerationReportSearchCondition(
                status = null,
                targetType = null,
                cursor = cursor,
            ),
            101,
        )).thenReturn(emptyList())

        val result = service.getReports(9, null, null, cursor.encode(), 999)

        assertEquals(emptyList(), result.items)
        assertEquals(false, result.hasNext)
        assertEquals(null, result.nextCursor)
    }

    @Test
    fun `관리자 신고 목록은 잘못된 커서를 거부한다`() {
        assertEquals(
            CommonErrorCode.INVALID_INPUT,
            assertBusinessException { service.getReports(9, null, null, "invalid", 20) }.errorCode,
        )
    }

    private fun request(
        action: ModerationAction,
        days: Long? = null,
        status: ModerationReportStatus = ModerationReportStatus.RESOLVED,
    ) = HandleModerationReportRequest(
        status = status, action = action, note = "운영 검토", restrictionDays = days
    )

    private fun markInReview(report: ModerationReport) {
        report.handle(ModerationReportStatus.IN_REVIEW, admin, Instant.EPOCH)
    }

    private fun report(
        fixture: Fixture,
        targetType: ModerationTargetType,
        targetId: Long,
        id: Long,
    ): ModerationReport {
        return ModerationReport(
            fixture.post.trip, fixture.report.reporter, targetType, targetId, fixture.target,
            ModerationReportReason.SPAM,
        ).apply {
            this.id = id
            handle(ModerationReportStatus.IN_REVIEW, admin, Instant.EPOCH)
        }
    }

    private fun fixture(
        type: PostType,
        targetType: ModerationTargetType = ModerationTargetType.POST,
    ): Fixture {
        val reporter = User("신고자").apply { id = 1 }
        val target = User("대상").apply { id = 2 }
        val trip = Trip(reporter, "여행", "KRW").apply { id = 10 }
        val participant = TripParticipant(trip, target, target.nickname,
            participantRole = TripParticipantRole.MEMBER, participantStatus = TripParticipantStatus.ACTIVE)
        val post = Post(trip = trip, author = participant, postType = type).apply { id = 20 }
        val report = ModerationReport(trip, reporter, targetType, if (targetType == ModerationTargetType.USER) 2 else 20, target,
            ModerationReportReason.SPAM).apply { id = 30 }
        return Fixture(target, post, report)
    }

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try { block(); error("BusinessException expected") } catch (exception: BusinessException) { exception }
    }
    private data class Fixture(val target: User, val post: Post, val report: ModerationReport)
}
