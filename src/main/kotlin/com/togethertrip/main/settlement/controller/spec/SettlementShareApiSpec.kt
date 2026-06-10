package com.togethertrip.main.settlement.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.settlement.dto.response.SettlementShareResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "SettlementShare", description = "정산 공유 API")
interface SettlementShareApiSpec {

    @Operation(
        summary = "정산 공유 조회",
        description = "정산 공유 토큰으로 정산 결과를 조회합니다.",
    )
    fun getSettlementByShareToken(
        token: String,
    ): ApiResponse<SettlementShareResponse>
}
