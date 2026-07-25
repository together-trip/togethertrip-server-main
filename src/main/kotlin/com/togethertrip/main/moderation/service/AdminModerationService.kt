package com.togethertrip.main.moderation.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.response.CursorResponse
import com.togethertrip.main.moderation.domain.ModerationAction
import com.togethertrip.main.moderation.domain.ModerationReportAudit
import com.togethertrip.main.moderation.domain.ModerationReportStatus
import com.togethertrip.main.moderation.domain.ModerationTargetType
import com.togethertrip.main.moderation.dto.request.HandleModerationReportRequest
import com.togethertrip.main.moderation.dto.response.ModerationReportResponse
import com.togethertrip.main.moderation.exception.ModerationErrorCode
import com.togethertrip.main.moderation.pagination.ModerationReportCursor
import com.togethertrip.main.moderation.repository.ModerationReportAuditRepository
import com.togethertrip.main.moderation.repository.ModerationReportRepository
import com.togethertrip.main.moderation.repository.ModerationReportSearchCondition
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.repository.PostCommentRepository
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.triprecap.repository.TripRecapRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserRole
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class AdminModerationService(
    private val moderationReportRepository: ModerationReportRepository,
    private val moderationReportAuditRepository: ModerationReportAuditRepository,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val postCommentRepository: PostCommentRepository,
    private val tripRecapRepository: TripRecapRepository,
) {
    fun getReports(
        adminUserId: Long,
        status: ModerationReportStatus?,
        targetType: ModerationTargetType?,
        cursor: String?,
        size: Int?,
    ): CursorResponse<ModerationReportResponse> {
        requireAdmin(adminUserId)
        val requestedSize = (size ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        val parsedCursor = cursor?.let(::parseCursor)
        val reports = moderationReportRepository.findReports(
            condition = ModerationReportSearchCondition(
                status = status,
                targetType = targetType,
                cursor = parsedCursor,
            ),
            limit = requestedSize + 1,
        )
        val responseItems = reports.take(requestedSize)
        val hasNext = reports.size > requestedSize
        val nextCursor = if (hasNext && responseItems.isNotEmpty()) {
            responseItems.last().let { ModerationReportCursor(it.createdAt, it.id).encode() }
        } else {
            null
        }
        return CursorResponse(
            items = responseItems.map(ModerationReportResponse::from),
            nextCursor = nextCursor,
            hasNext = hasNext,
            size = responseItems.size,
        )
    }

    @Transactional
    fun handle(adminUserId: Long, reportId: Long, request: HandleModerationReportRequest): ModerationReportResponse {
        val admin = requireAdmin(adminUserId)
        val report = moderationReportRepository.findLockedById(reportId)
            ?: throw BusinessException(ModerationErrorCode.REPORT_NOT_FOUND)
        val nextStatus = request.status ?: throw BusinessException(ModerationErrorCode.INVALID_ACTION)
        val action = request.action ?: throw BusinessException(ModerationErrorCode.INVALID_ACTION)
        val previousStatus = report.status
        val now = Instant.now()

        validateTransition(previousStatus, nextStatus, action, report.targetType)
        applyAction(report.targetType, report.targetId, report.targetUser, action, request, now)
        report.handle(nextStatus, admin, now)
        moderationReportAuditRepository.save(
            ModerationReportAudit(report, admin, action, previousStatus, nextStatus, request.note)
        )
        return ModerationReportResponse.from(report)
    }

    private fun validateTransition(
        current: ModerationReportStatus,
        next: ModerationReportStatus,
        action: ModerationAction,
        targetType: ModerationTargetType,
    ) {
        val transitionAllowed = when (current) {
            ModerationReportStatus.PENDING -> next == ModerationReportStatus.IN_REVIEW
            ModerationReportStatus.IN_REVIEW ->
                next == ModerationReportStatus.RESOLVED || next == ModerationReportStatus.REJECTED
            ModerationReportStatus.RESOLVED, ModerationReportStatus.REJECTED -> false
        }
        if (!transitionAllowed) throw BusinessException(ModerationErrorCode.INVALID_STATUS_TRANSITION)

        if (next == ModerationReportStatus.IN_REVIEW || next == ModerationReportStatus.REJECTED) {
            if (action != ModerationAction.NONE) throw BusinessException(ModerationErrorCode.INVALID_ACTION)
            return
        }

        val actionAllowed = when (targetType) {
            ModerationTargetType.POST -> action in setOf(
                ModerationAction.NONE, ModerationAction.HIDE, ModerationAction.DELETE,
                ModerationAction.RESTRICT_USER, ModerationAction.UNRESTRICT_USER,
            )
            ModerationTargetType.COMMENT -> action in setOf(
                ModerationAction.NONE, ModerationAction.HIDE, ModerationAction.DELETE,
                ModerationAction.RESTRICT_USER, ModerationAction.UNRESTRICT_USER,
            )
            ModerationTargetType.USER -> action in setOf(
                ModerationAction.NONE, ModerationAction.RESTRICT_USER, ModerationAction.UNRESTRICT_USER,
            )
            ModerationTargetType.TRIP_RECAP -> action in setOf(
                ModerationAction.NONE, ModerationAction.HIDE, ModerationAction.DELETE,
            )
        }
        if (!actionAllowed) throw BusinessException(ModerationErrorCode.INVALID_ACTION)
    }

    private fun applyAction(
        type: ModerationTargetType,
        targetId: Long,
        targetUser: User,
        action: ModerationAction,
        request: HandleModerationReportRequest,
        now: Instant,
    ) {
        when (action) {
            ModerationAction.NONE -> Unit
            ModerationAction.HIDE -> when (type) {
                ModerationTargetType.POST -> getRecordPost(targetId).hideByModeration(now)
                ModerationTargetType.COMMENT -> getComment(targetId).hideByModeration(now)
                ModerationTargetType.TRIP_RECAP -> getRecap(targetId).hideByModeration(now)
                ModerationTargetType.USER -> invalidAction()
            }
            ModerationAction.DELETE -> when (type) {
                ModerationTargetType.POST -> getRecordPost(targetId).deleteByModeration(now)
                ModerationTargetType.COMMENT -> getComment(targetId).deleteByModeration(now)
                ModerationTargetType.TRIP_RECAP -> getRecap(targetId).deleteByModeration(now)
                ModerationTargetType.USER -> invalidAction()
            }
            ModerationAction.RESTRICT_USER -> {
                if (type == ModerationTargetType.TRIP_RECAP) invalidAction()
                val until = request.restrictionDays?.let { now.plus(it, ChronoUnit.DAYS) }
                targetUser.restrictModeration(request.note, until, now)
            }
            ModerationAction.UNRESTRICT_USER -> targetUser.clearModerationRestriction(now)
        }
    }

    private fun getRecordPost(id: Long) = postRepository.findById(id).orElse(null)
        ?.takeIf { it.postType == PostType.RECORD }
        ?: throw BusinessException(ModerationErrorCode.INVALID_ACTION)
    private fun getComment(id: Long) = postCommentRepository.findByIdAndDeletedAtIsNull(id)
        ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
    private fun getRecap(id: Long) = tripRecapRepository.findByIdAndDeletedAtIsNull(id)
        ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
    private fun invalidAction(): Nothing = throw BusinessException(ModerationErrorCode.INVALID_ACTION)
    private fun parseCursor(cursor: String): ModerationReportCursor {
        return try {
            ModerationReportCursor.decode(cursor)
        } catch (_: RuntimeException) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun requireAdmin(id: Long): User {
        val user = userRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        if (user.role != UserRole.ADMIN) throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        return user
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
    }
}
