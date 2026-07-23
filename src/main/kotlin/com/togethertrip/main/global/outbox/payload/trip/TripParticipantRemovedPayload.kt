package com.togethertrip.main.global.outbox.payload.trip

import com.togethertrip.main.global.outbox.payload.common.DefaultOutboxRecipientPayload
import com.togethertrip.main.global.outbox.payload.common.OutboxNotificationPayload
import java.time.Instant

data class TripParticipantRemovedPayload(
    override val recipients: List<DefaultOutboxRecipientPayload>,
    val actorUserId: Long,
    val tripId: Long,
    val participantId: Long,
    val tripName: String,
    val actorDisplayName: String,
    override val occurredAt: Instant,
    override val eventVersion: Int = 1,
) : OutboxNotificationPayload<DefaultOutboxRecipientPayload> {

    override fun withRecipients(recipients: List<DefaultOutboxRecipientPayload>): TripParticipantRemovedPayload =
        copy(recipients = recipients)
}
