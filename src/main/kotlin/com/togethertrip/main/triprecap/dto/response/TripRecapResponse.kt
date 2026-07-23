package com.togethertrip.main.triprecap.dto.response

import com.togethertrip.main.triprecap.domain.TripRecap
import com.togethertrip.main.triprecap.domain.TripRecapStatus
import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.triprecap.domain.TripRecapScene

data class TripRecapResponse(
    val recapId: Long,
    val tripId: Long,
    val style: TripRecapStyle,
    val status: TripRecapStatus,
    val scenes: List<TripRecapSceneResponse>,
) {
    companion object {
        fun from(
            recap: TripRecap,
            scenes: List<TripRecapScene>,
        ): TripRecapResponse {
            return TripRecapResponse(
                recapId = recap.id,
                tripId = recap.trip.id,
                style = recap.style,
                status = recap.status,
                scenes = scenes.map { scene ->
                    TripRecapSceneResponse.from(
                        tripId = recap.trip.id,
                        scene = scene,
                    )
                },
            )
        }
    }
}
