package com.togethertrip.main.trip.domain

/**
 * 여행방 정산 상태 (trips.settlement_status).
 * - OPEN: 정산 진행 가능 (거래 추가/수정 가능)
 * - SETTLED: 최종 정산 완료
 */
enum class TripSettlementStatus {
    OPEN,
    SETTLED
}
