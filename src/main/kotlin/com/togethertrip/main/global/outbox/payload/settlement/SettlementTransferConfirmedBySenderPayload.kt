package com.togethertrip.main.global.outbox.payload.settlement

import com.togethertrip.main.global.outbox.payload.common.DefaultOutboxRecipientPayload
import com.togethertrip.main.global.outbox.payload.common.OutboxNotificationPayload
import java.math.BigDecimal
import java.time.Instant

data class SettlementTransferConfirmedBySenderPayload(
    override val recipients: List<DefaultOutboxRecipientPayload>,
    val actorUserId: Long,
    val tripId: Long,
    val settlementId: Long,
    val settlementTransferId: Long,
    val tripName: String,
    val actorDisplayName: String,
    val amount: BigDecimal,
    val currency: String,
    override val occurredAt: Instant,
    override val eventVersion: Int = 1,
) : OutboxNotificationPayload<DefaultOutboxRecipientPayload> {

    override fun withRecipients(recipients: List<DefaultOutboxRecipientPayload>): SettlementTransferConfirmedBySenderPayload =
        copy(recipients = recipients)
}
