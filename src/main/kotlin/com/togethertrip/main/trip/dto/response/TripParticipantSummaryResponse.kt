package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import java.time.Instant

data class TripParticipantSummaryResponse(
    val id: Long,
    val userId: Long?,
    val displayName: String,
    val profileImageUrl: String?,
    val participantRole: TripParticipantRole,
    val participantStatus: TripParticipantStatus,
    val joinedAt: Instant?,
) {
    companion object {
        fun from(participant: TripParticipant): TripParticipantSummaryResponse {
            return TripParticipantSummaryResponse(
                id = participant.id,
                userId = participant.user?.id,
                displayName = participant.displayName,
                profileImageUrl = participant.profileImageUrl,
                participantRole = participant.participantRole,
                participantStatus = participant.participantStatus,
                joinedAt = participant.joinedAt,
            )
        }
    }
}
