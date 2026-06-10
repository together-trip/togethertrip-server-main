package com.togethertrip.main.settlement.service.support

import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.snapshot.SettlementSnapshotBalance
import com.togethertrip.main.settlement.domain.snapshot.SettlementSnapshotPayload
import com.togethertrip.main.settlement.dto.response.SettlementParticipantBalanceResponse
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class SettlementSnapshotMapper(
    private val objectMapper: ObjectMapper,
) {

    fun write(balances: List<SettlementParticipantBalanceResponse>): String {
        val payload = SettlementSnapshotPayload(
            balances = balances.map { balance ->
                SettlementSnapshotBalance(
                    participantId = balance.participantId,
                    userId = balance.userId,
                    displayName = balance.displayName,
                    profileImageUrl = balance.profileImageUrl,
                    participantStatus = balance.participantStatus,
                    paidAmount = balance.paidAmount,
                    shareAmount = balance.shareAmount,
                    netAmount = balance.netAmount,
                )
            }
        )

        return objectMapper.writeValueAsString(payload)
    }

    fun read(settlement: Settlement): SettlementSnapshotPayload {
        return objectMapper.readValue(settlement.snapshotPayload, SettlementSnapshotPayload::class.java)
    }
}
