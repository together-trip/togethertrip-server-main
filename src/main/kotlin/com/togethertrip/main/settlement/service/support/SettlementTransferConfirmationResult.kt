package com.togethertrip.main.settlement.service.support

import com.togethertrip.main.settlement.domain.SettlementTransferRow
import com.togethertrip.main.settlement.domain.SettlementTransferStatus

data class SettlementTransferConfirmationResult(
    val transferRow: SettlementTransferRow,
    val confirmationChanged: Boolean,
) {

    val completedChanged: Boolean =
        confirmationChanged && transferRow.getStatus() == SettlementTransferStatus.COMPLETED.name
}
