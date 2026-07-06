package com.togethertrip.main.global.outbox.domain

enum class OutboxAggregateType {
    TRIP,
    TRIP_RECAP,
    POST,
    SETTLEMENT,
    SETTLEMENT_TRANSFER,
}
