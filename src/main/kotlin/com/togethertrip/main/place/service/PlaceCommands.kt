package com.togethertrip.main.place.service

import java.math.BigDecimal

data class PlaceAutocompleteCommand(
    val userId: Long,
    val query: String,
    val sessionToken: String?,
    val languageCode: String,
) {
    override fun toString(): String {
        return "PlaceAutocompleteCommand(queryLength=${query.length}, " +
            "hasSessionToken=${!sessionToken.isNullOrBlank()}, languageCode=$languageCode)"
    }
}

data class PlaceDetailCommand(
    val userId: Long,
    val placeId: String,
    val sessionToken: String?,
    val languageCode: String,
) {
    override fun toString(): String {
        return "PlaceDetailCommand(placeIdLength=${placeId.length}, " +
            "hasSessionToken=${!sessionToken.isNullOrBlank()}, languageCode=$languageCode)"
    }
}

data class PlaceReverseGeocodeCommand(
    val userId: Long,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val languageCode: String,
) {
    override fun toString(): String {
        return "PlaceReverseGeocodeCommand(coordinates=***, languageCode=$languageCode)"
    }
}
