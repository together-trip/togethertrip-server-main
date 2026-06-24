package com.togethertrip.main.global.outbox.service

import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.domain.OutboxStatus
import com.togethertrip.main.global.outbox.repository.OutboxEventRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OutboxEventDispatchServiceTest {

    private lateinit var outboxEventRepository: OutboxEventRepository

    @BeforeEach
    fun setUp() {
        outboxEventRepository = mock(OutboxEventRepository::class.java)
    }

    @Test
    fun `대기 이벤트를 sender에 넘기고 성공하면 PUBLISHED로 변경한다`() {
        val event = outboxEvent()
        val sender = RecordingOutboxEventSender()
        val service = OutboxEventDispatchService(
            outboxEventRepository = outboxEventRepository,
            outboxEventSender = sender,
        )

        `when`(
            outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, 50),
            )
        ).thenReturn(listOf(event))

        val result = service.dispatchPending()

        assertEquals(1, result.requestedCount)
        assertEquals(1, result.publishedCount)
        assertEquals(0, result.failedCount)
        assertEquals(listOf(1L), sender.sentEventIds)
        assertEquals(OutboxStatus.PUBLISHED, event.status)
        assertNotNull(event.publishedAt)
    }

    @Test
    fun `sender가 실패하면 FAILED로 변경하고 retryCount를 증가시킨다`() {
        val event = outboxEvent()
        val service = OutboxEventDispatchService(
            outboxEventRepository = outboxEventRepository,
            outboxEventSender = FailingOutboxEventSender(),
        )

        `when`(
            outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, 50),
            )
        ).thenReturn(listOf(event))

        val result = service.dispatchPending()

        assertEquals(1, result.requestedCount)
        assertEquals(0, result.publishedCount)
        assertEquals(1, result.failedCount)
        assertEquals(OutboxStatus.FAILED, event.status)
        assertEquals(1, event.retryCount)
        assertEquals(null, event.publishedAt)
    }

    @Test
    fun `조회 개수는 1 이상 500 이하로 제한한다`() {
        val service = OutboxEventDispatchService(
            outboxEventRepository = outboxEventRepository,
            outboxEventSender = RecordingOutboxEventSender(),
        )

        `when`(
            outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, 500),
            )
        ).thenReturn(emptyList())

        service.dispatchPending(limit = 1000)

        verify(outboxEventRepository).findByStatusOrderByCreatedAtAsc(
            OutboxStatus.PENDING,
            PageRequest.of(0, 500),
        )
    }

    private fun outboxEvent(): OutboxEvent {
        return OutboxEvent(
            aggregateType = "TRIP",
            aggregateId = 10L,
            eventType = "TRIP_PARTICIPANTS_ADDED",
            payload = """{"eventVersion":1,"recipients":[{"userId":1}]}""",
        ).apply {
            id = 1L
            createdAt = Instant.parse("2026-06-22T12:00:00Z")
            updatedAt = Instant.parse("2026-06-22T12:00:00Z")
        }
    }

    private class RecordingOutboxEventSender : OutboxEventSender {

        val sentEventIds = mutableListOf<Long>()

        override fun send(event: OutboxEvent) {
            sentEventIds.add(event.id)
        }
    }

    private class FailingOutboxEventSender : OutboxEventSender {

        override fun send(event: OutboxEvent) {
            throw RuntimeException("dispatch failed")
        }
    }
}
