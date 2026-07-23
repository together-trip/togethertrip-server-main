package com.togethertrip.main.settlement.domain.snapshot

import com.togethertrip.main.trip.domain.TripParticipantStatus
import java.math.BigDecimal

data class SettlementSnapshotBalance(
    val participantId: Long,
    val userId: Long?,
    val displayName: String,
    val profileImageUrl: String?,
    val participantStatus: TripParticipantStatus,
    val paidAmount: BigDecimal,
    val shareAmount: BigDecimal,
    val netAmount: BigDecimal,
)
