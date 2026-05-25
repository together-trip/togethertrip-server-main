package com.togethertrip.main.settlement.domain

enum class SettlementTransferStatus {
    PENDING,
    SENDER_CONFIRMED,
    RECEIVER_CONFIRMED,
    COMPLETED,
    CANCELLED
}
