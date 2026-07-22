package com.togethertrip.main.global.outbox.service

import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxEventDispatchService(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxEventSender: OutboxEventSender,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun dispatchPending(
        limit: Int = DEFAULT_LIMIT,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    ): OutboxEventDispatchResult {
        val requestedCount = limit.coerceIn(1, MAX_LIMIT)
        val requestedMaxAttempts = maxAttempts.coerceIn(1, MAX_ATTEMPTS_LIMIT)
        val events = outboxEventRepository.findPendingForDispatch(requestedCount, requestedMaxAttempts)
        var publishedCount = 0
        var failedCount = 0

        events.forEach { event ->
            if (dispatch(event)) {
                publishedCount += 1
            } else {
                failedCount += 1
            }
        }

        return OutboxEventDispatchResult(
            requestedCount = events.size,
            publishedCount = publishedCount,
            failedCount = failedCount,
        )
    }

    private fun dispatch(event: OutboxEvent): Boolean {
        return try {
            outboxEventSender.send(event)
            event.markPublished()
            true
        } catch (exception: Exception) {
            event.markFailed()
            logger.warn(
                "outbox event dispatch failed. eventId={}, aggregateType={}, aggregateId={}, eventType={}",
                event.id,
                event.aggregateType,
                event.aggregateId,
                event.eventType,
                exception,
            )
            false
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 500
        const val DEFAULT_MAX_ATTEMPTS = 5
        const val MAX_ATTEMPTS_LIMIT = 100
    }
}
