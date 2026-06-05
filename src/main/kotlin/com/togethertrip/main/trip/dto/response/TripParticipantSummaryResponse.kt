package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.TripParticipant
import java.time.Instant

data class TripParticipantSummaryResponse(
    val id: Long,
    val userId: Long?,
    val displayName: String,
    val profileImageUrl: String?,
    val participantRole: String,
    val participantStatus: String,
    val joinedAt: Instant?,
) {
    companion object {
        fun from(participant: TripParticipant): TripParticipantSummaryResponse {
            return TripParticipantSummaryResponse(
                id = participant.id,
                userId = participant.user?.id,
                displayName = participant.displayName,
                profileImageUrl = participant.profileImageUrl,
                participantRole = participant.participantRole.name,
                participantStatus = participant.participantStatus.name,
                joinedAt = participant.joinedAt,
            )
        }
    }
}
