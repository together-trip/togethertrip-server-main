package com.togethertrip.main.settlement.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.settlement.dto.response.SettlementShareResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "SettlementShare", description = "정산 공유 API")
interface SettlementShareApiSpec {

    @Operation(
        summary = "정산 공유 조회",
        description = "정산 공유 토큰으로 확정된 정산 결과를 조회합니다. " +
            "인증이 필요하지 않으며, 응답은 캐시되지 않습니다.",
    )
    fun getSettlementByShareToken(
        token: String,
    ): ResponseEntity<ApiResponse<SettlementShareResponse>>
}
