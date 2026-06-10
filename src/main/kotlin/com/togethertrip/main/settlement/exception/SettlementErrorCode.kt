package com.togethertrip.main.settlement.exception

import com.togethertrip.main.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class SettlementErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    SETTLEMENT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SETTLEMENT_NOT_FOUND",
        "정산 정보를 찾을 수 없습니다.",
    ),

    SETTLEMENT_ALREADY_CONFIRMED(
        HttpStatus.CONFLICT,
        "SETTLEMENT_ALREADY_CONFIRMED",
        "이미 확정된 정산이 있습니다.",
    ),

    SETTLEMENT_NOT_CONFIRMED(
        HttpStatus.BAD_REQUEST,
        "SETTLEMENT_NOT_CONFIRMED",
        "확정된 정산만 처리할 수 있습니다.",
    ),

    SETTLEMENT_TRIP_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "SETTLEMENT_TRIP_MISMATCH",
        "정산이 해당 여행에 속하지 않습니다.",
    ),

    SETTLEMENT_TOTAL_MISMATCH(
        HttpStatus.CONFLICT,
        "SETTLEMENT_TOTAL_MISMATCH",
        "결제 금액 합계와 부담 금액 합계가 일치하지 않습니다.",
    ),

    SETTLEMENT_SHARE_TOKEN_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SETTLEMENT_SHARE_TOKEN_NOT_FOUND",
        "정산 공유 토큰을 찾을 수 없습니다.",
    ),

    SETTLEMENT_TRANSFER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SETTLEMENT_TRANSFER_NOT_FOUND",
        "송금 정보를 찾을 수 없습니다.",
    ),

    SETTLEMENT_TRANSFER_TRIP_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "SETTLEMENT_TRANSFER_TRIP_MISMATCH",
        "송금 정보가 해당 여행에 속하지 않습니다.",
    ),

    SETTLEMENT_TRANSFER_ACCESS_DENIED(
        HttpStatus.FORBIDDEN,
        "SETTLEMENT_TRANSFER_ACCESS_DENIED",
        "해당 송금을 확인할 권한이 없습니다.",
    ),

    INVALID_SETTLEMENT_TRANSFER_STATUS(
        HttpStatus.BAD_REQUEST,
        "INVALID_SETTLEMENT_TRANSFER_STATUS",
        "유효하지 않은 송금 상태입니다.",
    ),

    INVALID_SETTLEMENT_TRANSFER_DIRECTION(
        HttpStatus.BAD_REQUEST,
        "INVALID_SETTLEMENT_TRANSFER_DIRECTION",
        "유효하지 않은 송금 방향입니다.",
    )
}
