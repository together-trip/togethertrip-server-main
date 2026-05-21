package com.togethertrip.main.transaction.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.transaction.dto.request.CreateTransactionRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionPaymentsRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionSharesRequest
import com.togethertrip.main.transaction.service.TransactionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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

@Tag(name = "Transaction", description = "거래 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/trips/{tripId}")
class TransactionController(
    private val transactionService: TransactionService,
) {

    @Operation(summary = "거래 등록", description = "여행 지출, 공동경비 충전, 공동경비 사용 거래를 등록합니다.")
    @PostMapping("/transactions")
    fun createTransaction(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: CreateTransactionRequest,
    ) {
    }

    @Operation(summary = "거래 목록 조회", description = "여행의 거래 목록을 조회합니다.")
    @GetMapping("/transactions")
    fun getTransactions(
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

    @Operation(summary = "거래 상세 조회", description = "특정 거래의 결제자와 부담자 정보를 조회합니다.")
    @GetMapping("/transactions/{transactionId}")
    fun getTransaction(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
    ) {
    }

    @Operation(summary = "거래 수정", description = "거래 금액, 통화, 카테고리, 설명, 발생일시, 위치 정보를 수정합니다.")
    @PatchMapping("/transactions/{transactionId}")
    fun updateTransaction(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
        @RequestBody request: UpdateTransactionRequest,
    ) {
    }

    @Operation(summary = "거래 삭제", description = "특정 거래를 삭제합니다.")
    @DeleteMapping("/transactions/{transactionId}")
    fun deleteTransaction(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
    ) {
    }

    @Operation(summary = "거래 결제자 목록 변경", description = "특정 거래의 결제자 목록과 결제 금액을 변경합니다.")
    @PutMapping("/transactions/{transactionId}/payments")
    fun updateTransactionPayments(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
        @RequestBody request: UpdateTransactionPaymentsRequest,
    ) {
    }

    @Operation(summary = "거래 부담자 목록 변경", description = "특정 거래의 부담자 목록과 부담 금액을 변경합니다.")
    @PutMapping("/transactions/{transactionId}/shares")
    fun updateTransactionShares(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable transactionId: Long,
        @RequestBody request: UpdateTransactionSharesRequest,
    ) {
    }

    @Operation(summary = "공동경비 잔액 조회", description = "여행의 공동경비 충전 금액, 사용 금액, 잔액을 조회합니다.")
    @GetMapping("/common-fund-balance")
    fun getCommonFundBalance(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "거래 통계 조회", description = "여행의 카테고리별, 참여자별 거래 통계를 조회합니다.")
    @GetMapping("/transaction-statistics")
    fun getTransactionStatistics(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) groupBy: String?,
    ) {
    }
}
