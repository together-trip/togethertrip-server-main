package com.togethertrip.main.settlement.service

import com.togethertrip.main.settlement.repository.SettlementTransferRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SettlementTransferService(
    private val settlementTransferRepository: SettlementTransferRepository,
)
