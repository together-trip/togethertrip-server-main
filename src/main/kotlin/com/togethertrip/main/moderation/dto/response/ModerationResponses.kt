package com.togethertrip.main.moderation.dto.response

import com.togethertrip.main.moderation.domain.ModerationReport
import com.togethertrip.main.moderation.domain.ModerationReportReason
import com.togethertrip.main.moderation.domain.ModerationReportStatus
import com.togethertrip.main.moderation.domain.ModerationTargetType
import com.togethertrip.main.moderation.domain.UserBlock
import java.time.Instant

data class ModerationReportResponse(
    val id: Long,
    val tripId: Long,
    val reporterUserId: Long,
    val targetType: ModerationTargetType,
    val targetId: Long,
    val targetUserId: Long,
    val reason: ModerationReportReason,
    val description: String?,
    val status: ModerationReportStatus,
    val handledByUserId: Long?,
    val handledAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun from(report: ModerationReport) = ModerationReportResponse(
            id = report.id,
            tripId = report.trip.id,
            reporterUserId = report.reporter.id,
            targetType = report.targetType,
            targetId = report.targetId,
            targetUserId = report.targetUser.id,
            reason = report.reason,
            description = report.description,
            status = report.status,
            handledByUserId = report.handledBy?.id,
            handledAt = report.handledAt,
            createdAt = report.createdAt,
        )
    }
}

data class BlockedUserResponse(
    val blockedUserId: Long,
    val displayName: String,
    val blockedAt: Instant,
) {
    companion object {
        fun from(block: UserBlock) = BlockedUserResponse(
            blockedUserId = block.blocked.id,
            displayName = block.blocked.nickname,
            blockedAt = block.createdAt,
        )
    }
}

data class BlockedUsersResponse(val items: List<BlockedUserResponse>)
