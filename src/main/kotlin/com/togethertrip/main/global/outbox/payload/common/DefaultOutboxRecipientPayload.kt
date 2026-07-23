package com.togethertrip.main.global.outbox.payload.common

data class DefaultOutboxRecipientPayload(
    override val userId: Long,
) : OutboxRecipientPayload
