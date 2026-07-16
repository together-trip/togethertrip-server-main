package com.togethertrip.main.place.client

import com.togethertrip.main.place.dto.PlaceDetailResponse
import com.togethertrip.main.place.dto.PlaceSuggestionResponse
import java.math.BigDecimal

interface PlaceSearchClient {
    fun autocomplete(
        query: String,
        sessionToken: String?,
        languageCode: String,
    ): List<PlaceSuggestionResponse>

    fun getPlace(
        placeId: String,
        sessionToken: String?,
        languageCode: String,
    ): PlaceDetailResponse

    fun reverseGeocode(
        latitude: BigDecimal,
        longitude: BigDecimal,
        languageCode: String,
    ): PlaceDetailResponse
}
