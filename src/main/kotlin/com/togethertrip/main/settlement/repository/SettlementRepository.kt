package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementRepository : JpaRepository<Settlement, Long> {

    fun findByTripIdAndStatus(tripId: Long, status: SettlementStatus): List<Settlement>

    // CONFIRMED 정산은 여행방당 하나만 존재 (uk_settlements_confirmed_trip)
    fun findFirstByTripIdAndStatus(tripId: Long, status: SettlementStatus): Settlement?

    fun findByShareToken(shareToken: String): Settlement?
}
