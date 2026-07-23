package com.togethertrip.main.place.dto

import java.math.BigDecimal

data class PlaceDetailResponse(
    val placeId: String?,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)
