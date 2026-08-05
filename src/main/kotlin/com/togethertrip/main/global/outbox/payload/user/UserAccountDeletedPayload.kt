package com.togethertrip.main.global.outbox.payload.user

import com.togethertrip.main.global.outbox.payload.common.OutboxLifecyclePayload
import java.time.Instant

data class UserAccountDeletedPayload(
    override val eventVersion: Int = 1,
    val userId: Long,
    override val occurredAt: Instant,
) : OutboxLifecyclePayload
