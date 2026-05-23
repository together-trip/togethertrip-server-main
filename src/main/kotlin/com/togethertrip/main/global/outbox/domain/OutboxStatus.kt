package com.togethertrip.main.global.outbox.domain

/**
 * Outbox 이벤트 발행 상태.
 * - PENDING: 발행 대기
 * - PUBLISHED: 발행 완료
 * - FAILED: 발행 실패 (재시도 대상)
 */
enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
