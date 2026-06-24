package com.togethertrip.main.global.outbox.payload.settlement

import com.togethertrip.main.global.outbox.payload.common.OutboxNotificationPayload
import java.time.Instant

data class SettlementConfirmedPayload(
    override val recipients: List<SettlementConfirmedRecipientPayload>,
    val actorUserId: Long,
    val tripId: Long,
    val settlementId: Long,
    val tripName: String,
    override val occurredAt: Instant,
    override val eventVersion: Int = 1,
) : OutboxNotificationPayload<SettlementConfirmedRecipientPayload> {

    override fun withRecipients(recipients: List<SettlementConfirmedRecipientPayload>): SettlementConfirmedPayload =
        copy(recipients = recipients)
}
