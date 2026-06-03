package com.togethertrip.main.settlement.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.settlement.controller.spec.SettlementApiSpec
import com.togethertrip.main.settlement.service.SettlementService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/trips/{tripId}")
class SettlementController(
    private val settlementService: SettlementService,
) : SettlementApiSpec {

    @PostMapping("/settlement-preview")
    override fun previewSettlement(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @GetMapping("/balance-summary")
    override fun getBalanceSummary(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @PostMapping("/settlements")
    override fun confirmSettlement(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @GetMapping("/settlements/{settlementId}")
    override fun getSettlement(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable settlementId: Long,
    ) {
    }

    @PostMapping("/settlements/{settlementId}/share-tokens")
    override fun createShareToken(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable settlementId: Long,
    ) {
    }
}
