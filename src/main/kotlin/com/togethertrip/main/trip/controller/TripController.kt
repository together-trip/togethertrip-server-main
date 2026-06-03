package com.togethertrip.main.trip.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.controller.spec.TripApiSpec
import com.togethertrip.main.trip.dto.request.CreateTripRequest
import com.togethertrip.main.trip.dto.request.UpdateTripCountriesRequest
import com.togethertrip.main.trip.dto.request.UpdateTripRequest
import com.togethertrip.main.trip.service.TripService
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
) : TripApiSpec {

    @PostMapping
    override fun createTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestBody request: CreateTripRequest,
    ) {
    }

    @GetMapping
    override fun getTrips(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ) {
    }

    @GetMapping("/{tripId}")
    override fun getTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @PatchMapping("/{tripId}")
    override fun updateTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: UpdateTripRequest,
    ) {
    }

    @DeleteMapping("/{tripId}")
    override fun deleteTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @PutMapping("/{tripId}/countries")
    override fun updateTripCountries(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: UpdateTripCountriesRequest,
    ) {
    }
}
