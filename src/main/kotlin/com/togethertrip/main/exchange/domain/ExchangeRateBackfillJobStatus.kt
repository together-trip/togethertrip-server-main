package com.togethertrip.main.exchange.domain

enum class ExchangeRateBackfillJobStatus {
    REQUESTED,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED_LOCKED,
}
