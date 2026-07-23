package com.togethertrip.main.triprecap.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.triprecap.dto.request.TripRecapCreateRequest
import com.togethertrip.main.triprecap.dto.request.TripRecapRetryRequest
import com.togethertrip.main.triprecap.dto.response.TripRecapCreateResponse
import com.togethertrip.main.triprecap.dto.response.TripRecapResponse
import com.togethertrip.main.triprecap.dto.response.TripRecapStatusResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "Trip Recap", description = "지난 여행 AI Recap API")
@SecurityRequirement(name = "bearerAuth")
interface TripRecapApiSpec {

    @Operation(summary = "여행 Recap 상태 조회", description = "여행 Recap 생성 가능 여부와 현재 상태를 조회합니다.")
    fun getStatus(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<TripRecapStatusResponse>

    @Operation(summary = "여행 Recap 생성 요청", description = "여행 종료 및 정산 완료 후 AI Recap 생성을 비동기로 요청합니다.")
    fun create(
        authUser: AuthUser,
        tripId: Long,
        request: TripRecapCreateRequest,
    ): ApiResponse<TripRecapCreateResponse>

    @Operation(summary = "여행 Recap 재시도", description = "실패한 Recap 생성을 스타일 재선택 후 다시 시도합니다.")
    fun retry(
        authUser: AuthUser,
        tripId: Long,
        request: TripRecapRetryRequest,
    ): ApiResponse<TripRecapCreateResponse>

    @Operation(summary = "여행 Recap 조회", description = "완성된 여행 Recap의 장면 이미지 목록을 조회합니다.")
    fun getRecap(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<TripRecapResponse>

    @Operation(summary = "여행 Recap 장면 이미지 조회", description = "여행 멤버 권한 확인 후 완성된 Recap 장면 이미지를 조회합니다.")
    fun getSceneImage(
        authUser: AuthUser,
        tripId: Long,
        sceneId: Long,
    ): ResponseEntity<ByteArray>
}
