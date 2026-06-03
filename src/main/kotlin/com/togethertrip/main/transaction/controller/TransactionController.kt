package com.togethertrip.main.transaction.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.transaction.controller.spec.TransactionApiSpec
import com.togethertrip.main.transaction.dto.request.CreateTransactionRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionPaymentsRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionSharesRequest
import com.togethertrip.main.transaction.service.TransactionService
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

@RestController
@RequestMapping("/api/trips/{tripId}")
class TransactionController(
    private val transactionService: TransactionService,
) : TransactionApiSpec {

    @PostMapping("/transactions")
    override fun createTransaction(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: CreateTransactionRequest,
    ) {
    }

    @GetMapping("/transactions")
    override fun getTransactions(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) participantId: Long?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ) {
    }

    @GetMapping("/transactions/{transactionId}")
    override fun getTransaction(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
    ) {
    }

    @PatchMapping("/transactions/{transactionId}")
    override fun updateTransaction(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
        @RequestBody request: UpdateTransactionRequest,
    ) {
    }

    @DeleteMapping("/transactions/{transactionId}")
    override fun deleteTransaction(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
    ) {
    }

    @GetMapping("/transactions/{transactionId}/events")
    override fun getTransactionEvents(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
    ) {
    }

    @PutMapping("/transactions/{transactionId}/payments")
    override fun updateTransactionPayments(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
        @RequestBody request: UpdateTransactionPaymentsRequest,
    ) {
    }

    @PutMapping("/transactions/{transactionId}/shares")
    override fun updateTransactionShares(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
        @RequestBody request: UpdateTransactionSharesRequest,
    ) {
    }

    @GetMapping("/common-fund-balance")
    override fun getCommonFundBalance(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @GetMapping("/transaction-statistics")
    override fun getTransactionStatistics(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) groupBy: String?,
    ) {
    }
}
