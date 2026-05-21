package com.togethertrip.main.settlement.service

import com.togethertrip.main.settlement.repository.SettlementRepository
import com.togethertrip.main.settlement.repository.SettlementTransferRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SettlementService(
    private val settlementRepository: SettlementRepository,
    private val settlementTransferRepository: SettlementTransferRepository,
)
