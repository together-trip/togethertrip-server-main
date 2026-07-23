package com.togethertrip.main.post.dto.request

import java.math.BigDecimal

interface PlaceRequest {
    val placeName: String?
    val latitude: BigDecimal?
    val longitude: BigDecimal?
}
