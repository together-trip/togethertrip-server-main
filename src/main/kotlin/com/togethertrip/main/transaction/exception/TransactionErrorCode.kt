package com.togethertrip.main.transaction.exception

import com.togethertrip.main.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class TransactionErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    TRANSACTION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "TRANSACTION_NOT_FOUND",
        "거래를 찾을 수 없습니다.",
    ),

    TRANSACTION_TRIP_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "TRANSACTION_TRIP_MISMATCH",
        "거래가 해당 여행에 속하지 않습니다.",
    ),

    EXCHANGE_RATE_NOT_READY(
        HttpStatus.BAD_REQUEST,
        "EXCHANGE_RATE_NOT_READY",
        "거래에 사용할 환율 정보가 없습니다.",
    ),

    INVALID_TRANSACTION_AMOUNT(
        HttpStatus.BAD_REQUEST,
        "INVALID_TRANSACTION_AMOUNT",
        "유효하지 않은 거래 금액입니다.",
    ),

    TRANSACTION_PAYMENT_TOTAL_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "TRANSACTION_PAYMENT_TOTAL_MISMATCH",
        "결제자 금액 합계가 거래 금액과 일치하지 않습니다.",
    ),

    TRANSACTION_SHARE_TOTAL_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "TRANSACTION_SHARE_TOTAL_MISMATCH",
        "부담자 금액 합계가 거래 금액과 일치하지 않습니다.",
    ),

    DUPLICATE_TRANSACTION_PARTICIPANT(
        HttpStatus.BAD_REQUEST,
        "DUPLICATE_TRANSACTION_PARTICIPANT",
        "동일한 참여자가 결제자 또는 부담자 목록에 중복 포함될 수 없습니다.",
    ),

    TRANSACTION_LOCKED_BY_SETTLEMENT(
        HttpStatus.CONFLICT,
        "TRANSACTION_LOCKED_BY_SETTLEMENT",
        "정산이 시작된 여행에서는 거래를 변경할 수 없습니다.",
    ),

    TRANSACTION_ALREADY_VOIDED(
        HttpStatus.CONFLICT,
        "TRANSACTION_ALREADY_VOIDED",
        "무효 처리된 거래는 변경할 수 없습니다.",
    )
}
