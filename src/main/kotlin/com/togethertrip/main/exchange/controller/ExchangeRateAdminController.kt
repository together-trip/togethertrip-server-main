package com.togethertrip.main.exchange.controller

import com.togethertrip.main.exchange.domain.ExchangeRateImportRunStatus
import com.togethertrip.main.exchange.dto.request.ExchangeRateBackfillRequest
import com.togethertrip.main.exchange.dto.response.ExchangeRateBackfillJobResponse
import com.togethertrip.main.exchange.dto.response.ExchangeRateImportRunResponse
import com.togethertrip.main.exchange.service.ExchangeRateAdminService
import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/admin/exchange-rates")
class ExchangeRateAdminController(
    private val exchangeRateAdminService: ExchangeRateAdminService,
) {

    @GetMapping("/import-runs")
    fun getImportRuns(
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
        @RequestParam(required = false) status: ExchangeRateImportRunStatus?,
    ): ApiResponse<List<ExchangeRateImportRunResponse>> {
        return ApiResponse.success(
            exchangeRateAdminService.getImportRuns(
                from = from,
                to = to,
                status = status,
            )
        )
    }

    @GetMapping("/backfills")
    fun getBackfillJobs(
        @RequestParam(defaultValue = "20") limit: Int,
    ): ApiResponse<List<ExchangeRateBackfillJobResponse>> {
        return ApiResponse.success(exchangeRateAdminService.getBackfillJobs(limit))
    }

    @PostMapping("/backfills")
    fun runBackfill(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestBody request: ExchangeRateBackfillRequest,
    ): ApiResponse<ExchangeRateBackfillJobResponse> {
        return ApiResponse.success(
            exchangeRateAdminService.runBackfill(
                requestedBy = authUser.userId,
                request = request,
            )
        )
    }
}
