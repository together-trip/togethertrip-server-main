package com.togethertrip.main.settlement.service

import com.togethertrip.main.settlement.repository.SettlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SettlementShareService(
    private val settlementRepository: SettlementRepository,
)
