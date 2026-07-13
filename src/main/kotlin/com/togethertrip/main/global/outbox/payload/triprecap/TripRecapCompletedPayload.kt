package com.togethertrip.main.global.outbox.payload.triprecap

import com.togethertrip.main.global.outbox.payload.common.DefaultOutboxRecipientPayload
import com.togethertrip.main.global.outbox.payload.common.OutboxNotificationPayload
import java.time.Instant

data class TripRecapCompletedPayload(
    override val recipients: List<DefaultOutboxRecipientPayload>,
    val tripId: Long,
    val tripRecapId: Long,
    val tripName: String,
    override val occurredAt: Instant,
    override val eventVersion: Int = 1,
) : OutboxNotificationPayload<DefaultOutboxRecipientPayload> {

    override fun withRecipients(
        recipients: List<DefaultOutboxRecipientPayload>,
    ): OutboxNotificationPayload<DefaultOutboxRecipientPayload> {
        return copy(recipients = recipients)
    }
}
