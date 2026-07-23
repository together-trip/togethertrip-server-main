package com.togethertrip.main.place.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.place.controller.spec.PlaceApiSpec
import com.togethertrip.main.place.dto.PlaceDetailResponse
import com.togethertrip.main.place.dto.PlaceSuggestionResponse
import com.togethertrip.main.place.service.PlaceService
import com.togethertrip.main.place.service.PlaceAutocompleteCommand
import com.togethertrip.main.place.service.PlaceDetailCommand
import com.togethertrip.main.place.service.PlaceReverseGeocodeCommand
import com.togethertrip.main.trip.security.RequireActiveTripParticipant
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@Validated
@RestController
@RequestMapping("/api/trips/{tripId}/places")
class PlaceController(
    private val placeService: PlaceService,
) : PlaceApiSpec {

    @GetMapping("/autocomplete")
    @RequireActiveTripParticipant
    override fun autocomplete(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam query: String,
        @RequestParam(required = false) sessionToken: String?,
        @RequestParam(defaultValue = "ko") languageCode: String,
    ): ApiResponse<List<PlaceSuggestionResponse>> {
        return ApiResponse.success(
            placeService.autocomplete(
                PlaceAutocompleteCommand(authUser.userId, query, sessionToken, languageCode),
            ),
        )
    }

    @GetMapping("/{placeId}")
    @RequireActiveTripParticipant
    override fun getPlace(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable placeId: String,
        @RequestParam(required = false) sessionToken: String?,
        @RequestParam(defaultValue = "ko") languageCode: String,
    ): ApiResponse<PlaceDetailResponse> {
        return ApiResponse.success(
            placeService.getPlace(
                PlaceDetailCommand(authUser.userId, placeId, sessionToken, languageCode),
            ),
        )
    }

    @GetMapping("/reverse")
    @RequireActiveTripParticipant
    override fun reverseGeocode(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam latitude: BigDecimal,
        @RequestParam longitude: BigDecimal,
        @RequestParam(defaultValue = "ko") languageCode: String,
    ): ApiResponse<PlaceDetailResponse> {
        return ApiResponse.success(
            placeService.reverseGeocode(
                PlaceReverseGeocodeCommand(authUser.userId, latitude, longitude, languageCode),
            ),
        )
    }
}
