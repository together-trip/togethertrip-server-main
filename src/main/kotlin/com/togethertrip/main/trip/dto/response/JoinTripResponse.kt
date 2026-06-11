package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.TripInvitation
import com.togethertrip.main.trip.domain.TripParticipant

data class JoinTripResponse(
    val tripId: Long,
    val participant: TripParticipantSummaryResponse,
    val invitationId: Long,
) {
    companion object {
        fun from(
            invitation: TripInvitation,
            participant: TripParticipant,
        ): JoinTripResponse {
            return JoinTripResponse(
                tripId = invitation.trip.id,
                participant = TripParticipantSummaryResponse.from(participant),
                invitationId = invitation.id,
            )
        }
    }
}
