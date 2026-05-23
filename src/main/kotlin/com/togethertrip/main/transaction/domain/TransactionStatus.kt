package com.togethertrip.main.transaction.domain

/**
 * 거래 상태.
 * - ACTIVE: 유효한 거래
 * - VOIDED: 무효 처리된 거래 (정산 집계에서 제외)
 */
enum class TransactionStatus {
    ACTIVE,
    VOIDED
}
