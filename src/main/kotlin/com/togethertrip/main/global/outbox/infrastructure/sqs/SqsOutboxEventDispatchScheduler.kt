package com.togethertrip.main.global.outbox.infrastructure.sqs

import com.togethertrip.main.global.outbox.service.OutboxEventDispatchService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SqsOutboxEventDispatchScheduler(
    private val dispatchService: OutboxEventDispatchService,
    private val sqsProperties: SqsOutboxProperties,
    private val dispatchProperties: SqsOutboxDispatchProperties,
) {

    @Scheduled(fixedDelayString = "\${outbox.dispatch.fixed-delay:PT3S}")
    fun dispatchPending() {
        if (!dispatchProperties.enabled || !sqsProperties.enabled || !sqsProperties.hasQueueUrl()) {
            return
        }

        dispatchService.dispatchPending(
            limit = dispatchProperties.limit,
            maxAttempts = dispatchProperties.maxAttempts,
        )
    }
}
