package com.togethertrip.main.global.outbox.service

import com.togethertrip.main.global.outbox.domain.OutboxEvent

fun interface OutboxEventSender {

    fun send(event: OutboxEvent)
}
