package com.togethertrip.main.global.outbox.domain

enum class OutboxAggregateType {
    TRIP,
    POST,
    SETTLEMENT,
    SETTLEMENT_TRANSFER,
}
