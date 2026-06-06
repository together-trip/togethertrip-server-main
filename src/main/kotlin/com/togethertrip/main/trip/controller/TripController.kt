package com.togethertrip.main.trip.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.controller.spec.TripApiSpec
import com.togethertrip.main.trip.dto.request.CreateTripRequest
import com.togethertrip.main.trip.dto.request.UpdateTripExchangeRateRequest
import com.togethertrip.main.trip.dto.request.UpdateTripCountriesRequest
import com.togethertrip.main.trip.dto.request.UpdateTripRequest
import com.togethertrip.main.trip.dto.response.TripCountriesResponse
import com.togethertrip.main.trip.dto.response.TripDetailResponse
import com.togethertrip.main.trip.dto.response.TripExchangeRateResponse
import com.togethertrip.main.trip.dto.response.TripListResponse
import com.togethertrip.main.trip.service.TripExchangeRateService
import com.togethertrip.main.trip.service.TripService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/trips")
class TripController(
    private val tripService: TripService,
    private val tripExchangeRateService: TripExchangeRateService,
) : TripApiSpec {

    @PostMapping
    override fun createTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @Valid @RequestBody request: CreateTripRequest,
    ): ApiResponse<TripDetailResponse> {
        return ApiResponse.success(
            tripService.createTrip(
                userId = authUser.userId,
                request = request,
            )
        )
    }

    @GetMapping
    override fun getTrips(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<TripListResponse> {
        return ApiResponse.success(
            tripService.getTrips(
                userId = authUser.userId,
                status = status,
                cursor = cursor,
                size = size,
            )
        )
    }

    @GetMapping("/{tripId}")
    override fun getTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ): ApiResponse<TripDetailResponse> {
        return ApiResponse.success(
            tripService.getTrip(
                userId = authUser.userId,
                tripId = tripId,
            )
        )
    }

    @PatchMapping("/{tripId}")
    override fun updateTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @Valid @RequestBody request: UpdateTripRequest,
    ): ApiResponse<TripDetailResponse> {
        return ApiResponse.success(
            tripService.updateTrip(
                userId = authUser.userId,
                tripId = tripId,
                request = request,
            )
        )
    }

    @DeleteMapping("/{tripId}")
    override fun deleteTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ): ApiResponse<Unit> {
        tripService.deleteTrip(
            userId = authUser.userId,
            tripId = tripId,
        )

        return ApiResponse.success()
    }

    @PutMapping("/{tripId}/countries")
    override fun updateTripCountries(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @Valid @RequestBody request: UpdateTripCountriesRequest,
    ): ApiResponse<TripCountriesResponse> {
        return ApiResponse.success(
            tripService.updateTripCountries(
                userId = authUser.userId,
                tripId = tripId,
                request = request,
            )
        )
    }

    @GetMapping("/{tripId}/exchange-rates")
    override fun getTripExchangeRates(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ): ApiResponse<List<TripExchangeRateResponse>> {
        return ApiResponse.success(
            tripExchangeRateService.getExchangeRates(
                userId = authUser.userId,
                tripId = tripId,
            )
        )
    }

    @PostMapping("/{tripId}/exchange-rates/refresh")
    override fun refreshTripExchangeRates(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ): ApiResponse<List<TripExchangeRateResponse>> {
        return ApiResponse.success(
            tripExchangeRateService.refreshExchangeRates(
                userId = authUser.userId,
                tripId = tripId,
            )
        )
    }

    @PatchMapping("/{tripId}/exchange-rates/{exchangeRateId}")
    override fun updateTripExchangeRate(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable exchangeRateId: Long,
        @Valid @RequestBody request: UpdateTripExchangeRateRequest,
    ): ApiResponse<TripExchangeRateResponse> {
        return ApiResponse.success(
            tripExchangeRateService.updateExchangeRate(
                userId = authUser.userId,
                tripId = tripId,
                exchangeRateId = exchangeRateId,
                request = request,
            )
        )
    }
}
