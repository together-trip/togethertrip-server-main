package com.togethertrip.main.settlement.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.settlement.controller.spec.SettlementApiSpec
import com.togethertrip.main.settlement.dto.response.BalanceSummaryResponse
import com.togethertrip.main.settlement.dto.response.SettlementPreviewResponse
import com.togethertrip.main.settlement.dto.response.SettlementResponse
import com.togethertrip.main.settlement.dto.response.SettlementShareTokenResponse
import com.togethertrip.main.settlement.service.SettlementService
import com.togethertrip.main.trip.security.RequireActiveTripParticipant
import com.togethertrip.main.trip.security.RequireTripOwner
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
    @RequireActiveTripParticipant
    override fun previewSettlement(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ): ApiResponse<SettlementPreviewResponse> {
        return ApiResponse.success(
            settlementService.previewSettlement(
                userId = authUser.userId,
                tripId = tripId,
            )
        )
    }

    @GetMapping("/balance-summary")
    @RequireActiveTripParticipant
    override fun getBalanceSummary(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ): ApiResponse<BalanceSummaryResponse> {
        return ApiResponse.success(
            settlementService.getBalanceSummary(
                userId = authUser.userId,
                tripId = tripId,
            )
        )
    }

    @PostMapping("/settlements")
    @RequireActiveTripParticipant
    @RequireTripOwner
    override fun confirmSettlement(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ): ApiResponse<SettlementResponse> {
        return ApiResponse.success(
            settlementService.confirmSettlement(
                userId = authUser.userId,
                tripId = tripId,
            )
        )
    }

    @GetMapping("/settlements/{settlementId}")
    @RequireActiveTripParticipant
    override fun getSettlement(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable settlementId: Long,
    ): ApiResponse<SettlementResponse> {
        return ApiResponse.success(
            settlementService.getSettlement(
                userId = authUser.userId,
                tripId = tripId,
                settlementId = settlementId,
            )
        )
    }

    @PostMapping("/settlements/{settlementId}/share-tokens")
    @RequireActiveTripParticipant
    @RequireTripOwner
    override fun createShareToken(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable settlementId: Long,
    ): ApiResponse<SettlementShareTokenResponse> {
        return ApiResponse.success(
            settlementService.createShareToken(
                userId = authUser.userId,
                tripId = tripId,
                settlementId = settlementId,
            )
        )
    }
}
