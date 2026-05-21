package com.togethertrip.main.settlement.controller

import com.togethertrip.main.settlement.service.SettlementService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Settlement", description = "정산 API")
@RestController
@RequestMapping("/api/settlements")
class SettlementShareController(
    private val settlementService: SettlementService,
) {

    @Operation(summary = "정산 공유 조회", description = "정산 공유 토큰으로 정산 결과를 조회합니다.")
    @GetMapping("/share/{shareToken}")
    fun getSettlementByShareToken(
        @PathVariable shareToken: String,
    ) {
    }
}
