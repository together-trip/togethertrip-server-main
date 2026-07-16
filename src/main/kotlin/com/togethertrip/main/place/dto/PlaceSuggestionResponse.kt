package com.togethertrip.main.place.dto

data class PlaceSuggestionResponse(
    val placeId: String,
    val name: String,
    val address: String?,
)
