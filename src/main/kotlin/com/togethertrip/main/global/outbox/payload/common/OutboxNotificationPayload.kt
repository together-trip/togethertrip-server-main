package com.togethertrip.main.global.outbox.payload.common

import java.time.Instant

interface OutboxNotificationPayload<R : OutboxRecipientPayload> {
    val eventVersion: Int
    val recipients: List<R>
    val occurredAt: Instant

    fun withRecipients(recipients: List<R>): OutboxNotificationPayload<R>
}
