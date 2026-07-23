package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.TripParticipant

object TripParticipantDisplay {

    fun displayName(participant: TripParticipant): String {
        return participant.user?.nickname ?: participant.displayName
    }

    fun profileImageUrl(participant: TripParticipant): String? {
        return participant.user?.profileImageUrl ?: participant.profileImageUrl
    }
}
