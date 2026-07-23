package com.togethertrip.main.global.outbox.repository

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.outbox.domain.OutboxEvent
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@MainIntegrationTest
class OutboxEventRepositoryConcurrencyTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val repository: OutboxEventRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    @Test
    fun `두 worker가 동시에 pending event를 조회해도 claim한 ID는 겹치지 않는다`() {
        val eventIds = createEvents(10)
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val selected = CountDownLatch(2)

        try {
            val futures = (0 until 2).map { worker ->
                executor.submit<Set<Long>> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    inTransaction {
                        val events = repository.findPendingForDispatch(10, 5)
                        selected.countDown()
                        assertTrue(selected.await(2, TimeUnit.SECONDS))
                        events.forEach {
                            it.markPublished(Instant.parse("2026-07-08T00:00:00Z").plusSeconds(worker.toLong()))
                        }
                        events.map { it.id }.toSet()
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val first = futures[0].get(10, TimeUnit.SECONDS)
            val second = futures[1].get(10, TimeUnit.SECONDS)
            assertEquals(emptySet(), first intersect second)
            assertEquals(eventIds, first union second)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun createEvents(count: Int): Set<Long> {
        return inTransaction {
            (0 until count).map { index ->
                val event = OutboxEvent(
                    aggregateType = "TRIP",
                    aggregateId = 100L + index,
                    eventType = "TRIP_PARTICIPANTS_ADDED",
                    payload = "{\"eventVersion\":1,\"index\":$index}",
                ).apply {
                    createdAt = Instant.parse("2026-07-08T00:00:00Z").plusSeconds(index.toLong())
                }
                entityManager.persist(event)
                event
            }.also { entityManager.flush() }.map { it.id }.toSet()
        }
    }

    private fun <T> inTransaction(block: () -> T): T {
        return requireNotNull(transactionTemplate.execute { block() })
    }
}
