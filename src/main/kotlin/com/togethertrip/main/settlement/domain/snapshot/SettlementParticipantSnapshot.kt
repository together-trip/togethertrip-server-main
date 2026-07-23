package com.togethertrip.main.settlement.domain.snapshot

import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.UserStatus

data class SettlementParticipantSnapshot(
    val participantId: Long,
    val userId: Long?,
    val displayName: String,
    val profileImageUrl: String?,
    val participantStatus: TripParticipantStatus,
    val isWithdrawnUser: Boolean,
    val requiresAutoConfirmation: Boolean,
) {
    companion object {
        fun from(row: SettlementParticipantRow): SettlementParticipantSnapshot {
            val userStatus = row.getUserStatus()?.let(UserStatus::valueOf)
            val participantStatus = TripParticipantStatus.valueOf(row.getParticipantStatus())
            val isWithdrawnUser = userStatus != null && userStatus != UserStatus.ACTIVE
            val requiresAutoConfirmation =
                row.getUserId() == null ||
                    participantStatus != TripParticipantStatus.ACTIVE ||
                    isWithdrawnUser

            return SettlementParticipantSnapshot(
                participantId = row.getParticipantId(),
                userId = if (isWithdrawnUser) null else row.getUserId(),
                displayName = if (isWithdrawnUser) WITHDRAWN_USER_DISPLAY_NAME else row.getDisplayName(),
                profileImageUrl = if (isWithdrawnUser) null else row.getProfileImageUrl(),
                participantStatus = participantStatus,
                isWithdrawnUser = isWithdrawnUser,
                requiresAutoConfirmation = requiresAutoConfirmation,
            )
        }
    }
}

interface SettlementParticipantRow {
    fun getParticipantId(): Long

    fun getUserId(): Long?

    fun getDisplayName(): String

    fun getProfileImageUrl(): String?

    fun getParticipantStatus(): String

    fun getUserStatus(): String?
}

private const val WITHDRAWN_USER_DISPLAY_NAME = "탈퇴한 사용자"
