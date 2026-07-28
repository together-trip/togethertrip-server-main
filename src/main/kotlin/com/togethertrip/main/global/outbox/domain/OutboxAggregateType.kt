package com.togethertrip.main.global.outbox.domain

enum class OutboxAggregateType {
    USER,
    TRIP,
    TRIP_RECAP,
    POST,
    SETTLEMENT,
    SETTLEMENT_TRANSFER,
}
