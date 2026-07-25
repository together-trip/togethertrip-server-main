package com.togethertrip.main.moderation.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.moderation.domain.ModerationReport
import com.togethertrip.main.moderation.domain.ModerationReportStatus
import com.togethertrip.main.moderation.domain.ModerationTargetType
import com.togethertrip.main.moderation.dto.request.CreateModerationReportRequest
import com.togethertrip.main.moderation.dto.response.ModerationReportResponse
import com.togethertrip.main.moderation.exception.ModerationErrorCode
import com.togethertrip.main.moderation.repository.ModerationReportRepository
import com.togethertrip.main.post.repository.PostCommentRepository
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.service.support.TripAccessResolver
import com.togethertrip.main.triprecap.repository.TripRecapRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ModerationReportService(
    private val tripAccessResolver: TripAccessResolver,
    private val userRepository: UserRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val postRepository: PostRepository,
    private val postCommentRepository: PostCommentRepository,
    private val tripRecapRepository: TripRecapRepository,
    private val moderationReportRepository: ModerationReportRepository,
) {
    @Transactional
    fun create(userId: Long, tripId: Long, request: CreateModerationReportRequest): ModerationReportResponse {
        val reporter = tripAccessResolver.getActiveUser(userId)
        val trip = tripAccessResolver.getAccessibleTrip(userId, tripId)
        val targetType = request.targetType ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
        val targetId = request.targetId ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
        val reason = request.reason ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
        val targetUser = resolveTargetUser(targetType, targetId, tripId)

        if (targetType != ModerationTargetType.TRIP_RECAP && targetUser.id == userId) {
            throw BusinessException(ModerationErrorCode.SELF_REPORT_NOT_ALLOWED)
        }
        if (moderationReportRepository.existsByReporterIdAndTripIdAndTargetTypeAndTargetIdAndStatusInAndDeletedAtIsNull(
                reporterId = userId,
                tripId = tripId,
                targetType = targetType,
                targetId = targetId,
                statuses = ACTIVE_STATUSES,
            )
        ) {
            throw BusinessException(ModerationErrorCode.DUPLICATE_REPORT)
        }

        val report = ModerationReport(
            trip = trip,
            reporter = reporter,
            targetType = targetType,
            targetId = targetId,
            targetUser = targetUser,
            reason = reason,
            description = request.description?.trim()?.takeIf(String::isNotBlank),
        )
        return try {
            ModerationReportResponse.from(moderationReportRepository.save(report))
        } catch (_: DataIntegrityViolationException) {
            throw BusinessException(ModerationErrorCode.DUPLICATE_REPORT)
        }
    }

    private fun resolveTargetUser(type: ModerationTargetType, targetId: Long, tripId: Long): User {
        return when (type) {
            ModerationTargetType.POST -> {
                val post = postRepository.findByIdAndTripIdAndDeletedAtIsNull(targetId, tripId)
                    ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
                post.author.user ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
            }
            ModerationTargetType.COMMENT -> {
                val comment = postCommentRepository.findByIdAndDeletedAtIsNull(targetId)
                    ?.takeIf { it.post.trip.id == tripId }
                    ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
                comment.author.user ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
            }
            ModerationTargetType.USER -> {
                if (!tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                        tripId, targetId, TripParticipantStatus.ACTIVE
                    )
                ) throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
                userRepository.findByIdAndDeletedAtIsNull(targetId)
                    ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
            }
            ModerationTargetType.TRIP_RECAP -> {
                val recap = tripRecapRepository.findByIdAndDeletedAtIsNull(targetId)
                    ?.takeIf { it.trip.id == tripId }
                    ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
                recap.requestedBy
            }
        }
    }

    private companion object {
        val ACTIVE_STATUSES = listOf(ModerationReportStatus.PENDING, ModerationReportStatus.IN_REVIEW)
    }
}
