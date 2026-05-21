package com.togethertrip.main.settlement.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.settlement.service.SettlementTransferService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "SettlementTransfer", description = "송금 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/trips/{tripId}/settlement-transfers")
class SettlementTransferController(
    private val settlementTransferService: SettlementTransferService,
) {

    @Operation(summary = "송금 목록 조회", description = "정산별 송금 목록을 조회합니다.")
    @GetMapping
    fun getTransfers(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam(required = false) settlementId: Long?,
        @RequestParam(required = false) participantId: Long?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) direction: String?,
    ) {
    }

    @Operation(summary = "송금자 확인", description = "보내는 사람이 송금 완료를 확인합니다.")
    @PatchMapping("/{transferId}/sender-confirmation")
    fun confirmAsSender(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transferId: Long,
    ) {
    }

    @Operation(summary = "수금자 확인", description = "받는 사람이 입금 완료를 확인합니다.")
    @PatchMapping("/{transferId}/receiver-confirmation")
    fun confirmAsReceiver(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transferId: Long,
    ) {
    }
}
