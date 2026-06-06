package com.togethertrip.main.trip.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.dto.request.CreateTripRequest
import com.togethertrip.main.trip.dto.request.UpdateTripExchangeRateRequest
import com.togethertrip.main.trip.dto.request.UpdateTripCountriesRequest
import com.togethertrip.main.trip.dto.request.UpdateTripRequest
import com.togethertrip.main.trip.dto.response.TripCountriesResponse
import com.togethertrip.main.trip.dto.response.TripDetailResponse
import com.togethertrip.main.trip.dto.response.TripExchangeRateResponse
import com.togethertrip.main.trip.dto.response.TripListResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Trip", description = "여행 API")
@SecurityRequirement(name = "bearerAuth")
interface TripApiSpec {

    @Operation(summary = "여행 생성", description = "여행명, 국가, 기간, 기본 통화, 동행자를 입력하여 여행을 생성합니다.")
    fun createTrip(
        authUser: AuthUser,
        request: CreateTripRequest,
    ): ApiResponse<TripDetailResponse>

    @Operation(summary = "여행 목록 조회", description = "현재 사용자가 참여한 여행 목록을 조회합니다.")
    fun getTrips(
        authUser: AuthUser,
        status: String?,
        cursor: String?,
        size: Int?,
    ): ApiResponse<TripListResponse>

    @Operation(summary = "여행 상세 조회", description = "여행 기본 정보, 국가, 참여자 정보를 조회합니다.")
    fun getTrip(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<TripDetailResponse>

    @Operation(summary = "여행 수정", description = "여행명, 국가, 기간, 기본 통화 정보를 수정합니다.")
    fun updateTrip(
        authUser: AuthUser,
        tripId: Long,
        request: UpdateTripRequest,
    ): ApiResponse<TripDetailResponse>

    @Operation(summary = "여행 삭제", description = "여행을 삭제합니다.")
    fun deleteTrip(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<Unit>

    @Operation(summary = "여행 국가 목록 변경", description = "여행의 국가 목록과 표시 순서를 변경합니다.")
    fun updateTripCountries(
        authUser: AuthUser,
        tripId: Long,
        request: UpdateTripCountriesRequest,
    ): ApiResponse<TripCountriesResponse>

    @Operation(summary = "여행 환율 목록 조회", description = "여행에서 사용하는 기준 환율 스냅샷 목록을 조회합니다.")
    fun getTripExchangeRates(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<List<TripExchangeRateResponse>>

    @Operation(summary = "여행 환율 재조회", description = "여행 국가 통화 기준으로 외부 환율 client를 호출해 여행 환율표를 갱신합니다.")
    fun refreshTripExchangeRates(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<List<TripExchangeRateResponse>>

    @Operation(summary = "여행 환율 수동 수정", description = "특정 여행 환율을 수동으로 수정합니다. 기존 거래 스냅샷은 재계산하지 않습니다.")
    fun updateTripExchangeRate(
        authUser: AuthUser,
        tripId: Long,
        exchangeRateId: Long,
        request: UpdateTripExchangeRateRequest,
    ): ApiResponse<TripExchangeRateResponse>
}
