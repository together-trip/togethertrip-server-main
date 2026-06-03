package com.togethertrip.main.user.dto.response

import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import java.time.Instant

data class MyTripParticipantResponse(
    val id: Long,
    val tripId: Long,
    val userId: Long?,
    val displayName: String,
    val profileImageUrl: String?,
    val participantRole: TripParticipantRole,
    val participantStatus: TripParticipantStatus,
    val joinedAt: Instant?,
    val leftAt: Instant?,
) {

    companion object {
        fun from(tripParticipant: TripParticipant): MyTripParticipantResponse {
            return MyTripParticipantResponse(
                id = tripParticipant.id,
                tripId = tripParticipant.trip.id,
                userId = tripParticipant.user?.id,
                displayName = tripParticipant.displayName,
                profileImageUrl = tripParticipant.profileImageUrl,
                participantRole = tripParticipant.participantRole,
                participantStatus = tripParticipant.participantStatus,
                joinedAt = tripParticipant.joinedAt,
                leftAt = tripParticipant.leftAt,
            )
        }
    }
}
