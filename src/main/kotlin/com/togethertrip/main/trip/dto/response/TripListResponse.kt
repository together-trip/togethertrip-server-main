package com.togethertrip.main.trip.dto.response

data class TripListResponse(
    val items: List<TripSummaryResponse>,
    val size: Int,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    companion object {
        fun from(
            items: List<TripSummaryResponse>,
            size: Int,
            nextCursor: String?,
            hasNext: Boolean,
        ): TripListResponse {
            return TripListResponse(
                items = items,
                size = size,
                nextCursor = nextCursor,
                hasNext = hasNext,
            )
        }
    }
}
