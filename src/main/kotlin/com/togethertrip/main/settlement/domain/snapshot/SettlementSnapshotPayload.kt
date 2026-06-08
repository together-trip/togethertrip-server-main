package com.togethertrip.main.settlement.domain.snapshot

data class SettlementSnapshotPayload(
    val balances: List<SettlementSnapshotBalance>,
)
