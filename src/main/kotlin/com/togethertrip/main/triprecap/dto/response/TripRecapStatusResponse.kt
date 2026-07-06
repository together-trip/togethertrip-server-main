package com.togethertrip.main.triprecap.dto.response

import com.togethertrip.main.triprecap.domain.TripRecapStatus
import com.togethertrip.main.triprecap.domain.TripRecapStyle

data class TripRecapStatusResponse(
    val available: Boolean,
    val status: TripRecapViewStatus,
    val recapId: Long?,
    val style: TripRecapStyle?,
) {
    companion object {
        fun unavailable(): TripRecapStatusResponse {
            return TripRecapStatusResponse(
                available = false,
                status = TripRecapViewStatus.NONE,
                recapId = null,
                style = null,
            )
        }

        fun none(): TripRecapStatusResponse {
            return TripRecapStatusResponse(
                available = true,
                status = TripRecapViewStatus.NONE,
                recapId = null,
                style = null,
            )
        }

        fun from(
            recapId: Long,
            status: TripRecapStatus,
            style: TripRecapStyle,
        ): TripRecapStatusResponse {
            return TripRecapStatusResponse(
                available = true,
                status = TripRecapViewStatus.valueOf(status.name),
                recapId = recapId,
                style = style,
            )
        }
    }
}
