package com.togethertrip.main.settlement.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.settlement.controller.spec.SettlementShareApiSpec
import com.togethertrip.main.settlement.dto.response.SettlementShareResponse
import com.togethertrip.main.settlement.service.SettlementShareService
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
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
    ): ResponseEntity<ApiResponse<SettlementShareResponse>> {
        // 인증 없이 열리는 응답이므로 중간 프록시와 브라우저 캐시에 남지 않게 한다.
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(
                ApiResponse.success(
                    settlementShareService.getSettlementByShareToken(token)
                )
            )
    }
}
