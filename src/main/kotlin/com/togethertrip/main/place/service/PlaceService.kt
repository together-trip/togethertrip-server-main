package com.togethertrip.main.place.service

import com.togethertrip.main.place.client.PlaceSearchClient
import com.togethertrip.main.place.dto.PlaceDetailResponse
import com.togethertrip.main.place.dto.PlaceSuggestionResponse
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class PlaceService(
    private val placeSearchClient: PlaceSearchClient,
    private val rateLimiter: PlaceRequestRateLimiter,
) {
    fun autocomplete(command: PlaceAutocompleteCommand): List<PlaceSuggestionResponse> {
        rateLimiter.validate(command.userId, PlaceOperation.AUTOCOMPLETE)
        return placeSearchClient.autocomplete(
            query = command.query.trim(),
            sessionToken = command.sessionToken?.trim()?.takeIf(String::isNotEmpty),
            languageCode = command.languageCode,
        )
    }

    fun getPlace(command: PlaceDetailCommand): PlaceDetailResponse {
        rateLimiter.validate(command.userId, PlaceOperation.DETAIL)
        return placeSearchClient.getPlace(
            placeId = command.placeId,
            sessionToken = command.sessionToken?.trim()?.takeIf(String::isNotEmpty),
            languageCode = command.languageCode,
        )
    }

    fun reverseGeocode(command: PlaceReverseGeocodeCommand): PlaceDetailResponse {
        rateLimiter.validate(command.userId, PlaceOperation.REVERSE_GEOCODE)
        return placeSearchClient.reverseGeocode(
            latitude = command.latitude,
            longitude = command.longitude,
            languageCode = command.languageCode,
        )
    }
}
