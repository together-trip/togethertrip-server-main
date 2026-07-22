package com.togethertrip.main.place.service

interface PlaceRequestRateLimiter {
    fun validate(
        userId: Long,
        operation: PlaceOperation,
    )
}

enum class PlaceOperation(
    val requestsPerMinute: Long,
) {
    AUTOCOMPLETE(60),
    DETAIL(30),
    REVERSE_GEOCODE(30),
}
