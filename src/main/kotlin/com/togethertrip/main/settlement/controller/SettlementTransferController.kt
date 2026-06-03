package com.togethertrip.main.settlement.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.settlement.controller.spec.SettlementTransferApiSpec
import com.togethertrip.main.settlement.service.SettlementTransferService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/trips/{tripId}/settlement-transfers")
class SettlementTransferController(
    private val settlementTransferService: SettlementTransferService,
) : SettlementTransferApiSpec {

    @GetMapping
    override fun getTransfers(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam(required = false) settlementId: Long?,
        @RequestParam(required = false) participantId: Long?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) direction: String?,
    ) {
    }

    @PatchMapping("/{transferId}/sender-confirmation")
    override fun confirmAsSender(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transferId: Long,
    ) {
    }

    @PatchMapping("/{transferId}/receiver-confirmation")
    override fun confirmAsReceiver(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transferId: Long,
    ) {
    }
}
