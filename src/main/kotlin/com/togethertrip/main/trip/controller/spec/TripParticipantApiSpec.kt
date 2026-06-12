package com.togethertrip.main.trip.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.dto.request.AddTripParticipantRequest
import com.togethertrip.main.trip.dto.request.LinkTripParticipantRequest
import com.togethertrip.main.trip.dto.request.UpdateTripParticipantRequest
import com.togethertrip.main.trip.dto.response.TripParticipantSummaryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "TripParticipant", description = "여행 참여자 API")
@SecurityRequirement(name = "bearerAuth")
interface TripParticipantApiSpec {

    @Operation(summary = "여행 참여자 추가", description = "여행에 임시 동행자를 추가합니다.")
    fun addParticipant(
        authUser: AuthUser,
        tripId: Long,
        request: AddTripParticipantRequest,
    ): ApiResponse<TripParticipantSummaryResponse>

    @Operation(summary = "여행 참여자 목록 조회", description = "여행의 참여자 목록을 조회합니다.")
    fun getParticipants(
        authUser: AuthUser,
        tripId: Long,
        status: String?,
        type: String?,
    ): ApiResponse<List<TripParticipantSummaryResponse>>

    @Operation(summary = "여행 참여자 상세 조회", description = "특정 여행 참여자 정보를 조회합니다.")
    fun getParticipant(
        authUser: AuthUser,
        tripId: Long,
        participantId: Long,
    ): ApiResponse<TripParticipantSummaryResponse>

    @Operation(summary = "여행 참여자 수정", description = "여행 owner가 임시 참여자의 표시명과 프로필 정보를 수정합니다.")
    fun updateParticipant(
        authUser: AuthUser,
        tripId: Long,
        participantId: Long,
        request: UpdateTripParticipantRequest,
    ): ApiResponse<TripParticipantSummaryResponse>

    @Operation(summary = "여행 참여자 제거", description = "여행 owner가 active 참여자를 제거합니다.")
    fun removeParticipant(
        authUser: AuthUser,
        tripId: Long,
        participantId: Long,
    ): ApiResponse<Unit>

    @Operation(summary = "임시 참여자 연결", description = "여행 owner가 임시 동행자를 지정한 active 회원과 연결합니다.")
    fun linkParticipant(
        authUser: AuthUser,
        tripId: Long,
        request: LinkTripParticipantRequest,
    ): ApiResponse<TripParticipantSummaryResponse>
}
