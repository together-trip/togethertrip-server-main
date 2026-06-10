package com.togethertrip.main.settlement.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.settlement.controller.spec.SettlementShareApiSpec
import com.togethertrip.main.settlement.dto.response.SettlementShareResponse
import com.togethertrip.main.settlement.service.SettlementShareService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/settlement-shares")
class SettlementShareController(
    private val settlementShareService: SettlementShareService,
) : SettlementShareApiSpec {

    @GetMapping
    override fun getSettlementByShareToken(
        @RequestParam token: String,
    ): ApiResponse<SettlementShareResponse> {
        return ApiResponse.success(
            settlementShareService.getSettlementByShareToken(token)
        )
    }
}
