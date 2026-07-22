package com.togethertrip.main.place.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.place.dto.PlaceDetailResponse
import com.togethertrip.main.place.dto.PlaceSuggestionResponse
import com.togethertrip.main.place.exception.PlaceErrorCode
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.math.BigDecimal

@Component
class GooglePlaceSearchClient(
    webClientBuilder: WebClient.Builder,
    private val properties: GooglePlacesProperties,
) : PlaceSearchClient {

    private val placesClient = webClientBuilder.clone()
        .baseUrl(properties.placesBaseUrl)
        .build()
    private val geocodingClient = webClientBuilder.clone()
        .baseUrl(properties.geocodingBaseUrl)
        .build()

    override fun autocomplete(
        query: String,
        sessionToken: String?,
        languageCode: String,
    ): List<PlaceSuggestionResponse> {
        requireConfigured()
        val response = execute {
            placesClient.post()
                .uri("/v1/places:autocomplete")
                .header(GOOGLE_API_KEY_HEADER, properties.apiKey)
                .header(GOOGLE_FIELD_MASK_HEADER, AUTOCOMPLETE_FIELD_MASK)
                .bodyValue(
                    GoogleAutocompleteRequest(
                        input = query,
                        sessionToken = sessionToken,
                        languageCode = languageCode,
                    ),
                )
                .retrieve()
                .bodyToMono<GoogleAutocompleteResponse>()
                .block(properties.timeout)
        } ?: return emptyList()

        return response.suggestions.mapNotNull { suggestion ->
            val prediction = suggestion.placePrediction ?: return@mapNotNull null
            val placeId = prediction.placeId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val name = prediction.structuredFormat?.mainText?.text
                ?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            PlaceSuggestionResponse(
                placeId = placeId,
                name = name,
                address = prediction.structuredFormat.secondaryText?.text,
            )
        }
    }

    override fun getPlace(
        placeId: String,
        sessionToken: String?,
        languageCode: String,
    ): PlaceDetailResponse {
        requireConfigured()
        val response = execute {
            placesClient.get()
                .uri { builder ->
                    builder.path("/v1/places/{placeId}")
                        .queryParam("languageCode", languageCode)
                        .apply {
                            if (!sessionToken.isNullOrBlank()) {
                                queryParam("sessionToken", sessionToken)
                            }
                        }
                        .build(placeId)
                }
                .header(GOOGLE_API_KEY_HEADER, properties.apiKey)
                .header(GOOGLE_FIELD_MASK_HEADER, PLACE_DETAIL_FIELD_MASK)
                .retrieve()
                .bodyToMono<GooglePlaceResponse>()
                .block(properties.timeout)
        } ?: throw BusinessException(PlaceErrorCode.PLACE_NOT_FOUND)

        val name = response.displayName?.text?.takeIf(String::isNotBlank)
            ?: throw BusinessException(PlaceErrorCode.PLACE_NOT_FOUND)
        val address = response.formattedAddress?.takeIf(String::isNotBlank)
            ?: name
        val location = response.location
            ?: throw BusinessException(PlaceErrorCode.PLACE_NOT_FOUND)

        return PlaceDetailResponse(
            placeId = response.id ?: placeId,
            name = name,
            address = address,
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }

    override fun reverseGeocode(
        latitude: BigDecimal,
        longitude: BigDecimal,
        languageCode: String,
    ): PlaceDetailResponse {
        requireConfigured()
        val response = execute {
            geocodingClient.get()
                .uri { builder ->
                    builder.path("/maps/api/geocode/json")
                        .queryParam("latlng", "$latitude,$longitude")
                        .queryParam("language", languageCode)
                        .queryParam("key", properties.apiKey)
                        .build()
                }
                .retrieve()
                .bodyToMono<GoogleGeocodingResponse>()
                .block(properties.timeout)
        } ?: throw BusinessException(PlaceErrorCode.PLACE_NOT_FOUND)

        if (response.status == ZERO_RESULTS_STATUS) {
            throw BusinessException(PlaceErrorCode.PLACE_NOT_FOUND)
        }
        if (response.status != OK_STATUS) {
            throw BusinessException(PlaceErrorCode.PLACE_SEARCH_FAILED)
        }
        val result = response.results.firstOrNull()
            ?: throw BusinessException(PlaceErrorCode.PLACE_NOT_FOUND)
        val address = result.formattedAddress?.takeIf(String::isNotBlank)
            ?: throw BusinessException(PlaceErrorCode.PLACE_NOT_FOUND)
        val placeId = result.placeId?.takeIf(String::isNotBlank)
        val place = placeId?.let {
            try {
                getPlace(it, null, languageCode)
            } catch (_: BusinessException) {
                null
            }
        }

        return PlaceDetailResponse(
            placeId = placeId,
            name = place?.name ?: address,
            address = place?.address ?: address,
            latitude = latitude,
            longitude = longitude,
        )
    }

    private fun requireConfigured() {
        if (properties.apiKey.isBlank()) {
            throw BusinessException(PlaceErrorCode.PLACE_SEARCH_NOT_CONFIGURED)
        }
    }

    private fun <T> execute(block: () -> T?): T? {
        return try {
            block()
        } catch (exception: BusinessException) {
            throw exception
        } catch (exception: Exception) {
            throw BusinessException(PlaceErrorCode.PLACE_SEARCH_FAILED)
        }
    }

    private data class GoogleAutocompleteRequest(
        val input: String,
        val sessionToken: String?,
        val languageCode: String,
    )

    private data class GoogleAutocompleteResponse(
        val suggestions: List<GoogleSuggestion> = emptyList(),
    )

    private data class GoogleSuggestion(
        val placePrediction: GooglePlacePrediction? = null,
    )

    private data class GooglePlacePrediction(
        val placeId: String? = null,
        val structuredFormat: GoogleStructuredFormat? = null,
    )

    private data class GoogleStructuredFormat(
        val mainText: GoogleText? = null,
        val secondaryText: GoogleText? = null,
    )

    private data class GoogleText(
        val text: String? = null,
    )

    private data class GooglePlaceResponse(
        val id: String? = null,
        val displayName: GoogleText? = null,
        val formattedAddress: String? = null,
        val location: GoogleLocation? = null,
    )

    private data class GoogleLocation(
        val latitude: BigDecimal,
        val longitude: BigDecimal,
    )

    private data class GoogleGeocodingResponse(
        val status: String? = null,
        val results: List<GoogleGeocodingResult> = emptyList(),
    )

    private data class GoogleGeocodingResult(
        @field:JsonProperty("place_id")
        val placeId: String? = null,
        @field:JsonProperty("formatted_address")
        val formattedAddress: String? = null,
    )

    companion object {
        private const val GOOGLE_API_KEY_HEADER = "X-Goog-Api-Key"
        private const val GOOGLE_FIELD_MASK_HEADER = "X-Goog-FieldMask"
        private const val AUTOCOMPLETE_FIELD_MASK =
            "suggestions.placePrediction.placeId," +
                "suggestions.placePrediction.structuredFormat"
        private const val PLACE_DETAIL_FIELD_MASK =
            "id,displayName,formattedAddress,location"
        private const val OK_STATUS = "OK"
        private const val ZERO_RESULTS_STATUS = "ZERO_RESULTS"
    }
}
