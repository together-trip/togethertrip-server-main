package com.togethertrip.main.transaction.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.response.CursorResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.transaction.dto.request.CreateTransactionRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionPaymentsRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionSharesRequest
import com.togethertrip.main.transaction.dto.response.TransactionDetailResponse
import com.togethertrip.main.transaction.dto.response.TransactionEventResponse
import com.togethertrip.main.transaction.dto.response.TransactionExchangeRatePreviewResponse
import com.togethertrip.main.transaction.dto.response.TransactionSummaryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.LocalDate

@Tag(name = "Transaction", description = "거래 API")
@SecurityRequirement(name = "bearerAuth")
interface TransactionApiSpec {

    @Operation(summary = "거래 등록", description = "여행 지출, 공동경비 충전, 공동경비 사용 거래를 등록합니다.")
    fun createTransaction(
        authUser: AuthUser,
        tripId: Long,
        request: CreateTransactionRequest,
    ): ApiResponse<TransactionDetailResponse>

    @Operation(summary = "거래 목록 조회", description = "여행의 거래 목록을 조회합니다.")
    fun getTransactions(
        authUser: AuthUser,
        tripId: Long,
        type: String?,
        category: String?,
        participantId: Long?,
        from: String?,
        to: String?,
        cursor: String?,
        size: Int?,
    ): ApiResponse<CursorResponse<TransactionSummaryResponse>>

    @Operation(summary = "거래 적용 환율 미리보기", description = "소비일 기준으로 거래 등록/수정 시 적용될 KRW 기준 환율을 조회합니다.")
    fun getTransactionExchangeRatePreview(
        authUser: AuthUser,
        tripId: Long,
        currency: String,
        spendingDate: LocalDate?,
    ): ApiResponse<TransactionExchangeRatePreviewResponse>

    @Operation(summary = "거래 상세 조회", description = "특정 거래의 결제자와 부담자 정보를 조회합니다.")
    fun getTransaction(
        authUser: AuthUser,
        tripId: Long,
        transactionId: Long,
    ): ApiResponse<TransactionDetailResponse>

    @Operation(summary = "거래 수정", description = "거래 금액, 통화, 카테고리, 설명, 발생일시, 위치 정보를 수정합니다.")
    fun updateTransaction(
        authUser: AuthUser,
        tripId: Long,
        transactionId: Long,
        request: UpdateTransactionRequest,
    ): ApiResponse<TransactionDetailResponse>

    @Operation(summary = "거래 삭제", description = "특정 거래를 삭제(무효 처리)합니다.")
    fun deleteTransaction(
        authUser: AuthUser,
        tripId: Long,
        transactionId: Long,
    ): ApiResponse<Unit>

    @Operation(summary = "거래 변경 이력 조회", description = "특정 거래의 변경 이벤트 이력(생성/조정/무효)을 버전 순으로 조회합니다.")
    fun getTransactionEvents(
        authUser: AuthUser,
        tripId: Long,
        transactionId: Long,
    ): ApiResponse<List<TransactionEventResponse>>

    @Operation(summary = "거래 결제자 목록 변경", description = "특정 거래의 결제자 목록과 결제 금액을 변경합니다.")
    fun updateTransactionPayments(
        authUser: AuthUser,
        tripId: Long,
        transactionId: Long,
        request: UpdateTransactionPaymentsRequest,
    ): ApiResponse<TransactionDetailResponse>

    @Operation(summary = "거래 부담자 목록 변경", description = "특정 거래의 부담자 목록과 부담 금액을 변경합니다.")
    fun updateTransactionShares(
        authUser: AuthUser,
        tripId: Long,
        transactionId: Long,
        request: UpdateTransactionSharesRequest,
    ): ApiResponse<TransactionDetailResponse>

    @Operation(summary = "공동경비 잔액 조회", description = "여행의 공동경비 충전 금액, 사용 금액, 잔액을 조회합니다.")
    fun getCommonFundBalance(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<Unit>

    @Operation(summary = "거래 통계 조회", description = "여행의 카테고리별, 참여자별 거래 통계를 조회합니다.")
    fun getTransactionStatistics(
        authUser: AuthUser,
        tripId: Long,
        from: String?,
        to: String?,
        groupBy: String?,
    ): ApiResponse<Unit>
}
