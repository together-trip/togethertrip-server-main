package com.togethertrip.main.global.outbox.repository

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.outbox.domain.OutboxEvent
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
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

    @BeforeEach
    fun clearOutboxEvents() {
        inTransaction {
            entityManager.createNativeQuery("delete from outbox_events").executeUpdate()
        }
    }

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

    @Test
    fun `앞선 event가 다른 worker에 잠겨 있으면 같은 aggregate의 후속 event를 claim하지 않는다`() {
        val eventIds = createEventsForAggregate(aggregateId = 200L, count = 2)
        val executor = Executors.newSingleThreadExecutor()
        val headLocked = CountDownLatch(1)
        val releaseHead = CountDownLatch(1)

        try {
            val headFuture = executor.submit<Long> {
                inTransaction {
                    val head = repository.findPendingForDispatch(1, 5).single()
                    headLocked.countDown()
                    assertTrue(releaseHead.await(10, TimeUnit.SECONDS))
                    head.markPublished(Instant.parse("2026-07-08T01:00:00Z"))
                    head.id
                }
            }

            assertTrue(headLocked.await(10, TimeUnit.SECONDS))

            val selectedWhileHeadLocked = inTransaction {
                repository.findPendingForDispatch(10, 5).map { it.id }
            }

            assertEquals(emptyList(), selectedWhileHeadLocked)
            releaseHead.countDown()
            assertEquals(eventIds.first(), headFuture.get(10, TimeUnit.SECONDS))

            val nextIds = inTransaction {
                repository.findPendingForDispatch(10, 5).map { it.id }
            }
            assertEquals(listOf(eventIds.last()), nextIds)
        } finally {
            releaseHead.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `최대 재시도에 도달한 failed head는 같은 aggregate의 후속 event를 차단한다`() {
        val eventIds = createEventsForAggregate(aggregateId = 300L, count = 2)

        inTransaction {
            val head = repository.findById(eventIds.first()).orElseThrow()
            repeat(5) { head.markFailed(Instant.parse("2026-07-08T02:00:00Z").plusSeconds(it.toLong())) }
        }

        val selectedIds = inTransaction {
            repository.findPendingForDispatch(10, 5).map { it.id }
        }

        assertEquals(emptyList(), selectedIds)
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

    private fun createEventsForAggregate(
        aggregateId: Long,
        count: Int,
    ): List<Long> {
        return inTransaction {
            (0 until count).map { index ->
                val event = OutboxEvent(
                    aggregateType = "SETTLEMENT_TRANSFER",
                    aggregateId = aggregateId,
                    eventType = if (index == 0) {
                        "SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER"
                    } else {
                        "SETTLEMENT_TRANSFER_COMPLETED"
                    },
                    payload = "{\"eventVersion\":1,\"index\":$index}",
                ).apply {
                    createdAt = Instant.parse("2026-07-08T00:30:00Z").plusSeconds(index.toLong())
                }
                entityManager.persist(event)
                event
            }.also { entityManager.flush() }.map { it.id }
        }
    }

    private fun <T> inTransaction(block: () -> T): T {
        return requireNotNull(transactionTemplate.execute { block() })
    }
}
