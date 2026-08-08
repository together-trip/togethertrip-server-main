package com.togethertrip.main.settlement.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.settlement.dto.response.BalanceSummaryResponse
import com.togethertrip.main.settlement.dto.response.SettlementPreviewResponse
import com.togethertrip.main.settlement.dto.response.SettlementResponse
import com.togethertrip.main.settlement.dto.response.SettlementShareTokenResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Settlement", description = "정산 API")
@SecurityRequirement(name = "bearerAuth")
interface SettlementApiSpec {

    @Operation(
        summary = "정산 미리보기",
        description = "여행의 거래 내역을 기준으로 정산 결과를 미리 계산합니다.",
    )
    fun previewSettlement(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<SettlementPreviewResponse>

    @Operation(
        summary = "참여자별 잔액 요약 조회",
        description = "참여자별 결제/부담/순정산 금액을 조회합니다.",
    )
    fun getBalanceSummary(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<BalanceSummaryResponse>

    @Operation(
        summary = "정산 확정",
        description = "정산 결과를 확정하고 송금 목록을 생성합니다.",
    )
    fun confirmSettlement(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<SettlementResponse>

    @Operation(
        summary = "정산 결과 조회",
        description = "확정된 정산 합계와 송금 목록을 조회합니다.",
    )
    fun getSettlement(
        authUser: AuthUser,
        tripId: Long,
        settlementId: Long,
    ): ApiResponse<SettlementResponse>

    @Operation(
        summary = "정산 공유 토큰 생성",
        description = "확정된 정산 결과를 공유하기 위한 토큰을 생성합니다. " +
            "이미 발급된 토큰이 있으면 같은 토큰을 반환합니다.",
    )
    fun createShareToken(
        authUser: AuthUser,
        tripId: Long,
        settlementId: Long,
    ): ApiResponse<SettlementShareTokenResponse>

    @Operation(
        summary = "정산 공유 토큰 회전",
        description = "새 공유 토큰을 발급하고 기존 공유 링크를 무효화합니다. " +
            "링크가 유출되었을 때 사용합니다.",
    )
    fun rotateShareToken(
        authUser: AuthUser,
        tripId: Long,
        settlementId: Long,
    ): ApiResponse<SettlementShareTokenResponse>
}
