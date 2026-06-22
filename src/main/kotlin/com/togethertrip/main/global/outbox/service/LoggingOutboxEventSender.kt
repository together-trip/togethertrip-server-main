package com.togethertrip.main.global.outbox.service

import com.togethertrip.main.global.outbox.domain.OutboxEvent
import org.slf4j.LoggerFactory

open class LoggingOutboxEventSender : OutboxEventSender {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun send(event: OutboxEvent) {
        logger.info(
            """
            
            [OUTBOX-LOGGING-SENDER]
            실제 외부 전송 없이 outbox 이벤트를 로그로만 확인합니다.
            eventId       : {}
            eventType     : {}
            aggregate     : {}#{}
            status        : {}
            retryCount    : {}
            createdAt     : {}
            payloadPreview: {}
            """.trimIndent(),
            event.id,
            event.eventType,
            event.aggregateType,
            event.aggregateId,
            event.status,
            event.retryCount,
            event.createdAt,
            payloadPreview(event.payload),
        )
    }

    private fun payloadPreview(payload: String): String {
        val compactPayload = payload
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (compactPayload.length <= MAX_PAYLOAD_PREVIEW_LENGTH) {
            compactPayload
        } else {
            compactPayload.take(MAX_PAYLOAD_PREVIEW_LENGTH) + "...(truncated)"
        }
    }

    private companion object {
        const val MAX_PAYLOAD_PREVIEW_LENGTH = 500
    }
}
