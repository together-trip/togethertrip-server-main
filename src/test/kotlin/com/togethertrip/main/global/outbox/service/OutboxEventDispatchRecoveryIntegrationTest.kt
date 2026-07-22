package com.togethertrip.main.global.outbox.service

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.domain.OutboxStatus
import com.togethertrip.main.global.outbox.repository.OutboxEventRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import kotlin.test.assertEquals

@MainIntegrationTest
class OutboxEventDispatchRecoveryIntegrationTest @Autowired constructor(
    private val repository: OutboxEventRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    @Test
    fun `첫 전송 실패 후 다음 dispatch에서 성공하면 PUBLISHED로 복구한다`() {
        val eventId = createPendingEvent("RECOVERY")
        val failingService = OutboxEventDispatchService(repository) { throw RuntimeException("first attempt failed") }
        val sentIds = mutableListOf<Long>()
        val recoveringService = OutboxEventDispatchService(repository) { event -> sentIds += event.id }

        val failed = inTransaction { failingService.dispatchPending(limit = 1, maxAttempts = 5) }
        assertEquals(1, failed.failedCount)
        assertState(eventId, OutboxStatus.FAILED, retryCount = 1)

        val recovered = inTransaction { recoveringService.dispatchPending(limit = 1, maxAttempts = 5) }

        assertEquals(1, recovered.publishedCount)
        assertEquals(listOf(eventId), sentIds)
        assertState(eventId, OutboxStatus.PUBLISHED, retryCount = 1)
    }

    @Test
    fun `최대 시도 횟수에 도달한 FAILED 이벤트는 더 이상 claim하지 않는다`() {
        val eventId = createPendingEvent("MAX_ATTEMPTS")
        val service = OutboxEventDispatchService(repository) { throw RuntimeException("always fails") }

        repeat(5) {
            val result = inTransaction { service.dispatchPending(limit = 1, maxAttempts = 5) }
            assertEquals(1, result.failedCount)
        }

        val exhausted = inTransaction { service.dispatchPending(limit = 1, maxAttempts = 5) }
        assertEquals(0, exhausted.requestedCount)
        assertState(eventId, OutboxStatus.FAILED, retryCount = 5)
    }

    private fun createPendingEvent(suffix: String): Long {
        return inTransaction {
            repository.saveAndFlush(
                OutboxEvent(
                    aggregateType = "TRIP",
                    aggregateId = System.nanoTime(),
                    eventType = "TEST_$suffix",
                    payload = """{"eventVersion":1}""",
                ).apply {
                    createdAt = Instant.EPOCH
                    updatedAt = Instant.EPOCH
                }
            ).id
        }
    }

    private fun assertState(eventId: Long, status: OutboxStatus, retryCount: Int) {
        inTransaction {
            val event = repository.findById(eventId).orElseThrow()
            assertEquals(status, event.status)
            assertEquals(retryCount, event.retryCount)
            assertEquals(status == OutboxStatus.PUBLISHED, event.publishedAt != null)
        }
    }

    private fun <T> inTransaction(block: () -> T): T {
        return requireNotNull(transactionTemplate.execute { block() })
    }
}
