package com.togethertrip.main.settlement.domain

/**
 * 정산 스냅샷 상태.
 * - DRAFT: 미확정 정산 스냅샷. 한 여행방에 여러 개 존재 가능.
 * - CONFIRMED: 확정 정산. 여행방당 하나만 존재 가능 (DB 부분 유니크 인덱스로 강제).
 * - CANCELLED: 취소된 정산 스냅샷.
 */
enum class SettlementStatus {
    DRAFT,
    CONFIRMED,
    CANCELLED
}
