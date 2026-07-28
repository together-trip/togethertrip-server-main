package com.togethertrip.main.global.outbox.payload.common

import java.time.Instant

interface OutboxLifecyclePayload {
    val eventVersion: Int
    val occurredAt: Instant
}
