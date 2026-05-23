package com.togethertrip.main.transaction.domain

/**
 * 거래 변경 이벤트 유형.
 * - CREATED: 거래 생성
 * - ADJUSTED: 거래 금액/항목 조정
 * - VOIDED: 거래 무효 처리
 */
enum class TransactionEventType {
    CREATED,
    ADJUSTED,
    VOIDED
}
