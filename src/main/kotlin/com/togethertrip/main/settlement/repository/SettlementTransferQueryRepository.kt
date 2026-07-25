package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.SettlementTransferRow

interface SettlementTransferQueryRepository {
    fun findTransferRows(condition: SettlementTransferSearchCondition): List<SettlementTransferRow>
}
