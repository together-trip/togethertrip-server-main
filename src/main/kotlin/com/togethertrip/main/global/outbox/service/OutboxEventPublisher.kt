package com.togethertrip.main.global.outbox.service

import com.togethertrip.main.global.outbox.domain.OutboxAggregateType
import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.common.OutboxNotificationPayload
import com.togethertrip.main.global.outbox.payload.common.OutboxRecipientPayload
import com.togethertrip.main.global.outbox.payload.common.OutboxLifecyclePayload
import com.togethertrip.main.global.outbox.repository.OutboxEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Service
class OutboxEventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun publishLifecycle(
        aggregateType: OutboxAggregateType,
        aggregateId: Long,
        eventType: OutboxEventType,
        payload: OutboxLifecyclePayload,
    ): OutboxEvent {
        return outboxEventRepository.save(
            OutboxEvent(
                aggregateType = aggregateType.name,
                aggregateId = aggregateId,
                eventType = eventType.name,
                payload = objectMapper.writeValueAsString(payload),
            )
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun <R : OutboxRecipientPayload> publish(
        aggregateType: OutboxAggregateType,
        aggregateId: Long,
        eventType: OutboxEventType,
        payload: OutboxNotificationPayload<R>,
    ): OutboxEvent? {
        val recipients = payload.recipients.distinctBy { it.userId }
        if (recipients.isEmpty()) {
            return null
        }

        val normalizedPayload = payload.withRecipients(recipients)
        val event = OutboxEvent(
            aggregateType = aggregateType.name,
            aggregateId = aggregateId,
            eventType = eventType.name,
            payload = objectMapper.writeValueAsString(normalizedPayload),
        )

        return outboxEventRepository.save(event)
    }
}
