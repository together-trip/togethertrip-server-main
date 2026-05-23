package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.SettlementTransfer
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementTransferRepository : JpaRepository<SettlementTransfer, Long> {

    fun findBySettlementId(settlementId: Long): List<SettlementTransfer>
}
