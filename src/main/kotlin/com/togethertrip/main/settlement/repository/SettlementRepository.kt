package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.Settlement
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementRepository : JpaRepository<Settlement, Long>
