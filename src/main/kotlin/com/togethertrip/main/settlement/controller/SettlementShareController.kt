package com.togethertrip.main.settlement.controller

import com.togethertrip.main.settlement.service.SettlementShareService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "SettlementShare", description = "정산 공유 API")
@RestController
@RequestMapping("/api/settlement-shares")
class SettlementShareController(
    private val settlementShareService: SettlementShareService,
) {

    @Operation(summary = "정산 공유 조회", description = "정산 공유 토큰으로 정산 결과를 조회합니다.")
    @GetMapping
    fun getSettlementByShareToken(
        @RequestParam token: String,
    ) {
    }
}
