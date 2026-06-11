package com.togethertrip.main.trip.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.dto.request.JoinTripRequest
import com.togethertrip.main.trip.dto.response.JoinTripResponse
import com.togethertrip.main.trip.dto.response.TripInviteInfoResponse
import com.togethertrip.main.trip.dto.response.TripInviteResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "TripInvite", description = "여행 초대 API")
@SecurityRequirement(name = "bearerAuth")
interface TripInviteApiSpec {

    @Operation(summary = "초대 코드 생성", description = "여행 참여용 초대 코드를 생성합니다.")
    fun createInviteCode(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<TripInviteResponse>

    @Operation(summary = "초대 링크 생성", description = "여행 참여용 초대 링크를 생성합니다.")
    fun createInviteLink(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<TripInviteResponse>

    @Operation(summary = "초대 정보 조회", description = "초대 코드 또는 초대 토큰으로 여행 초대 정보를 조회합니다.")
    fun getInviteInfo(
        authUser: AuthUser,
        code: String?,
        token: String?,
    ): ApiResponse<TripInviteInfoResponse>

    @Operation(summary = "초대 참여", description = "초대 코드 또는 초대 토큰으로 여행에 참여합니다.")
    fun joinTrip(
        authUser: AuthUser,
        request: JoinTripRequest,
    ): ApiResponse<JoinTripResponse>
}
