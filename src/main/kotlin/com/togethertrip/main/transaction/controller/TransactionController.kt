package com.togethertrip.main.transaction.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.response.CursorResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.transaction.controller.spec.TransactionApiSpec
import com.togethertrip.main.transaction.dto.request.CreateTransactionRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionPaymentsRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionSharesRequest
import com.togethertrip.main.transaction.dto.response.CommonFundBalanceResponse
import com.togethertrip.main.transaction.dto.response.TransactionDetailResponse
import com.togethertrip.main.transaction.dto.response.TransactionEventResponse
import com.togethertrip.main.transaction.dto.response.TransactionExchangeRatePreviewResponse
import com.togethertrip.main.transaction.dto.response.TransactionStatisticsResponse
import com.togethertrip.main.transaction.dto.response.TransactionSummaryResponse
import com.togethertrip.main.transaction.service.TransactionService
import com.togethertrip.main.trip.security.RequireActiveTripParticipant
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/trips/{tripId}")
class TransactionController(
    private val transactionService: TransactionService,
) : TransactionApiSpec {

    @PostMapping("/transactions")
    @RequireActiveTripParticipant
    override fun createTransaction(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @Valid @RequestBody request: CreateTransactionRequest,
    ): ApiResponse<TransactionDetailResponse> {
        return ApiResponse.success(
            transactionService.createTransaction(
                userId = authUser.userId,
                tripId = tripId,
                request = request,
            )
        )
    }

    @GetMapping("/transactions")
    @RequireActiveTripParticipant
    override fun getTransactions(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) participantId: Long?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<CursorResponse<TransactionSummaryResponse>> {
        return ApiResponse.success(
            transactionService.getTransactions(
                userId = authUser.userId,
                tripId = tripId,
                type = type,
                participantId = participantId,
                cursor = cursor,
                size = size,
            )
        )
    }

    @GetMapping("/transactions/exchange-rate")
    @RequireActiveTripParticipant
    override fun getTransactionExchangeRatePreview(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam currency: String,
        @RequestParam(required = false) spendingDate: LocalDate?,
    ): ApiResponse<TransactionExchangeRatePreviewResponse> {
        return ApiResponse.success(
            transactionService.getTransactionExchangeRatePreview(
                userId = authUser.userId,
                tripId = tripId,
                currency = currency,
                spendingDate = spendingDate,
            )
        )
    }

    @GetMapping("/transactions/{transactionId}")
    @RequireActiveTripParticipant
    override fun getTransaction(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
    ): ApiResponse<TransactionDetailResponse> {
        return ApiResponse.success(
            transactionService.getTransaction(
                userId = authUser.userId,
                tripId = tripId,
                transactionId = transactionId,
            )
        )
    }

    @PatchMapping("/transactions/{transactionId}")
    @RequireActiveTripParticipant
    override fun updateTransaction(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
        @Valid @RequestBody request: UpdateTransactionRequest,
    ): ApiResponse<TransactionDetailResponse> {
        return ApiResponse.success(
            transactionService.updateTransaction(
                userId = authUser.userId,
                tripId = tripId,
                transactionId = transactionId,
                request = request,
            )
        )
    }

    @DeleteMapping("/transactions/{transactionId}")
    @RequireActiveTripParticipant
    override fun deleteTransaction(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
    ): ApiResponse<Unit> {
        transactionService.deleteTransaction(
            userId = authUser.userId,
            tripId = tripId,
            transactionId = transactionId,
        )

        return ApiResponse.success()
    }

    @GetMapping("/transactions/{transactionId}/events")
    @RequireActiveTripParticipant
    override fun getTransactionEvents(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
    ): ApiResponse<List<TransactionEventResponse>> {
        return ApiResponse.success(
            transactionService.getTransactionEvents(
                userId = authUser.userId,
                tripId = tripId,
                transactionId = transactionId,
            )
        )
    }

    @PutMapping("/transactions/{transactionId}/payments")
    @RequireActiveTripParticipant
    override fun updateTransactionPayments(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
        @Valid @RequestBody request: UpdateTransactionPaymentsRequest,
    ): ApiResponse<TransactionDetailResponse> {
        return ApiResponse.success(
            transactionService.updateTransactionPayments(
                userId = authUser.userId,
                tripId = tripId,
                transactionId = transactionId,
                request = request,
            )
        )
    }

    @PutMapping("/transactions/{transactionId}/shares")
    @RequireActiveTripParticipant
    override fun updateTransactionShares(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
        @Valid @RequestBody request: UpdateTransactionSharesRequest,
    ): ApiResponse<TransactionDetailResponse> {
        return ApiResponse.success(
            transactionService.updateTransactionShares(
                userId = authUser.userId,
                tripId = tripId,
                transactionId = transactionId,
                request = request,
            )
        )
    }

    @GetMapping("/common-fund-balance")
    @RequireActiveTripParticipant
    override fun getCommonFundBalance(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ): ApiResponse<CommonFundBalanceResponse> {
        return ApiResponse.success(
            transactionService.getCommonFundBalance(
                userId = authUser.userId,
                tripId = tripId,
            )
        )
    }

    @GetMapping("/transaction-statistics")
    @RequireActiveTripParticipant
    override fun getTransactionStatistics(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) groupBy: String?,
    ): ApiResponse<TransactionStatisticsResponse> {
        return ApiResponse.success(
            transactionService.getTransactionStatistics(
                userId = authUser.userId,
                tripId = tripId,
                from = from,
                to = to,
                groupBy = groupBy,
            )
        )
    }
}
