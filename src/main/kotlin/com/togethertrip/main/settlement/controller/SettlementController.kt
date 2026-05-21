package com.togethertrip.main.settlement.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.settlement.service.SettlementService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Settlement", description = "정산 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/trips/{tripId}/settlements")
class SettlementController(
    private val settlementService: SettlementService,
) {

    @Operation(summary = "정산 미리보기", description = "여행의 거래 내역을 기준으로 정산 결과를 미리 계산합니다.")
    @PostMapping("/preview")
    fun previewSettlement(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "정산 확정", description = "여행의 정산 결과를 확정하고 송금 목록을 생성합니다.")
    @PostMapping
    fun confirmSettlement(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "정산 공유 토큰 생성", description = "확정된 정산 결과를 공유하기 위한 토큰을 생성합니다.")
    @PostMapping("/{settlementId}/share-token")
    fun createShareToken(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable settlementId: Long,
    ) {
    }

    @Operation(summary = "정산 결과 조회", description = "확정된 정산 합계와 송금 목록을 조회합니다.")
    @GetMapping("/{settlementId}")
    fun getSettlement(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable settlementId: Long,
    ) {
    }

    // ── 송금 ──────────────────────────────────────────────────────────────

    @Operation(summary = "송금 목록 조회", description = "정산별 송금 목록을 조회합니다.")
    @GetMapping("/{settlementId}/transfers")
    fun getTransfers(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable settlementId: Long,
    ) {
    }

    @Operation(summary = "내 송금 목록 조회", description = "현재 사용자가 보내거나 받아야 하는 송금 항목을 조회합니다.")
    @GetMapping("/{settlementId}/transfers/me")
    fun getMyTransfers(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable settlementId: Long,
    ) {
    }

    @Operation(summary = "송금자 확인", description = "보내는 사람이 송금 완료를 확인합니다.")
    @PatchMapping("/{settlementId}/transfers/{transferId}/sender-confirm")
    fun confirmAsSender(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable settlementId: Long,
        @PathVariable transferId: Long,
    ) {
    }

    @Operation(summary = "수금자 확인", description = "받는 사람이 입금 완료를 확인합니다.")
    @PatchMapping("/{settlementId}/transfers/{transferId}/receiver-confirm")
    fun confirmAsReceiver(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable settlementId: Long,
        @PathVariable transferId: Long,
    ) {
    }
}
