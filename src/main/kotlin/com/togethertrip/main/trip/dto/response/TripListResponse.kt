package com.togethertrip.main.trip.dto.response

data class TripListResponse(
    val items: List<TripSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)
