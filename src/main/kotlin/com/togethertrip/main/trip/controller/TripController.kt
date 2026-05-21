package com.togethertrip.main.trip.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.dto.request.CreateTripRequest
import com.togethertrip.main.trip.dto.request.UpdateTripCountriesRequest
import com.togethertrip.main.trip.dto.request.UpdateTripRequest
import com.togethertrip.main.trip.service.TripService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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

@Tag(name = "Trip", description = "여행 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/trips")
class TripController(
    private val tripService: TripService,
) {

    @Operation(summary = "여행 생성", description = "여행명, 국가, 기간, 기본 통화, 동행자를 입력하여 여행을 생성합니다.")
    @PostMapping
    fun createTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestBody request: CreateTripRequest,
    ) {
    }

    @Operation(summary = "여행 목록 조회", description = "현재 사용자가 참여한 여행 목록을 조회합니다.")
    @GetMapping
    fun getTrips(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ) {
    }

    @Operation(summary = "여행 상세 조회", description = "여행 기본 정보, 국가, 참여자 정보를 조회합니다.")
    @GetMapping("/{tripId}")
    fun getTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "여행 수정", description = "여행명, 국가, 기간, 기본 통화 정보를 수정합니다.")
    @PatchMapping("/{tripId}")
    fun updateTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: UpdateTripRequest,
    ) {
    }

    @Operation(summary = "여행 삭제", description = "여행을 삭제합니다.")
    @DeleteMapping("/{tripId}")
    fun deleteTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "여행 국가 목록 변경", description = "여행의 국가 목록과 표시 순서를 변경합니다.")
    @PutMapping("/{tripId}/countries")
    fun updateTripCountries(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: UpdateTripCountriesRequest,
    ) {
    }
}
