package com.togethertrip.main.settlement.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.settlement.dto.response.SettlementTransferResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "SettlementTransfer", description = "송금 API")
@SecurityRequirement(name = "bearerAuth")
interface SettlementTransferApiSpec {

    @Operation(
        summary = "송금 목록 조회",
        description = "정산별 송금 목록을 조회합니다.",
    )
    fun getTransfers(
        authUser: AuthUser,
        tripId: Long,
        settlementId: Long?,
        participantId: Long?,
        status: String?,
        direction: String?,
    ): ApiResponse<List<SettlementTransferResponse>>

    @Operation(
        summary = "송금자 확인",
        description = "보내는 사람이 송금 완료를 확인합니다.",
    )
    fun confirmAsSender(
        authUser: AuthUser,
        tripId: Long,
        transferId: Long,
    ): ApiResponse<SettlementTransferResponse>

    @Operation(
        summary = "수금자 확인",
        description = "받는 사람이 입금 완료를 확인합니다.",
    )
    fun confirmAsReceiver(
        authUser: AuthUser,
        tripId: Long,
        transferId: Long,
    ): ApiResponse<SettlementTransferResponse>
}
