package com.togethertrip.main.global.outbox.service

import com.togethertrip.main.global.outbox.domain.OutboxAggregateType
import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.domain.OutboxStatus
import com.togethertrip.main.global.outbox.payload.common.DefaultOutboxRecipientPayload
import com.togethertrip.main.global.outbox.payload.common.OutboxNotificationPayload
import com.togethertrip.main.global.outbox.payload.common.OutboxLifecyclePayload
import com.togethertrip.main.global.outbox.payload.settlement.SettlementConfirmedPayload
import com.togethertrip.main.global.outbox.payload.settlement.SettlementConfirmedRecipientPayload
import com.togethertrip.main.global.outbox.payload.settlement.SettlementTransferSummaryItemPayload
import com.togethertrip.main.global.outbox.payload.settlement.SettlementTransferSummaryPayload
import com.togethertrip.main.global.outbox.payload.trip.TripParticipantsAddedPayload
import com.togethertrip.main.global.outbox.payload.user.UserAccountDeletedPayload
import com.togethertrip.main.global.outbox.repository.OutboxEventRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OutboxEventPublisherTest {

    private lateinit var outboxEventRepository: OutboxEventRepository
    private lateinit var publisher: OutboxEventPublisher

    @BeforeEach
    fun setUp() {
        outboxEventRepository = mock(OutboxEventRepository::class.java)
        publisher = OutboxEventPublisher(
            outboxEventRepository = outboxEventRepository,
            objectMapper = jacksonObjectMapper(),
        )
    }

    @Test
    fun `payload를 jsonb 문자열로 직렬화해 PENDING outbox 이벤트를 저장한다`() {
        `when`(outboxEventRepository.save(any(OutboxEvent::class.java))).thenAnswer { invocation ->
            invocation.arguments[0] as OutboxEvent
        }

        val result = publisher.publish(
            aggregateType = OutboxAggregateType.TRIP,
            aggregateId = 20L,
            eventType = OutboxEventType.TRIP_PARTICIPANTS_ADDED,
            payload = tripParticipantsAddedPayload(
                recipients = listOf(
                    DefaultOutboxRecipientPayload(userId = 11L),
                    DefaultOutboxRecipientPayload(userId = 12L),
                ),
            ),
        )

        val event = assertNotNull(result)
        assertEquals(OutboxAggregateType.TRIP.name, event.aggregateType)
        assertEquals(20L, event.aggregateId)
        assertEquals(OutboxEventType.TRIP_PARTICIPANTS_ADDED.name, event.eventType)
        assertEquals(OutboxStatus.PENDING, event.status)

        val payload = jacksonObjectMapper().readTree(event.payload)
        assertEquals(1, payload["eventVersion"].intValue())
        assertEquals(10L, payload["actorUserId"].longValue())
        assertEquals(20L, payload["tripId"].longValue())
        assertEquals("일본 여행", payload["tripName"].stringValue())
        assertEquals("2026-06-22T12:00:00Z", payload["occurredAt"].stringValue())
        assertEquals(2, payload["recipients"].size())
        assertEquals(11L, payload["recipients"][0]["userId"].longValue())
        assertEquals(12L, payload["recipients"][1]["userId"].longValue())
    }

    @Test
    fun `중복 수신자는 userId 기준으로 제거하고 처음 등장한 순서를 유지한다`() {
        `when`(outboxEventRepository.save(any(OutboxEvent::class.java))).thenAnswer { invocation ->
            invocation.arguments[0] as OutboxEvent
        }

        publisher.publish(
            aggregateType = OutboxAggregateType.TRIP,
            aggregateId = 20L,
            eventType = OutboxEventType.TRIP_PARTICIPANTS_ADDED,
            payload = tripParticipantsAddedPayload(
                recipients = listOf(
                    DefaultOutboxRecipientPayload(userId = 12L),
                    DefaultOutboxRecipientPayload(userId = 11L),
                    DefaultOutboxRecipientPayload(userId = 12L),
                    DefaultOutboxRecipientPayload(userId = 13L),
                    DefaultOutboxRecipientPayload(userId = 11L),
                ),
            ),
        )

        val captor = ArgumentCaptor.forClass(OutboxEvent::class.java)
        verify(outboxEventRepository).save(captor.capture())

        val payload = jacksonObjectMapper().readTree(captor.value.payload)
        assertEquals(3, payload["recipients"].size())
        assertEquals(12L, payload["recipients"][0]["userId"].longValue())
        assertEquals(11L, payload["recipients"][1]["userId"].longValue())
        assertEquals(13L, payload["recipients"][2]["userId"].longValue())
    }

    @Test
    fun `수신자가 없으면 outbox 이벤트를 저장하지 않는다`() {
        val result = publisher.publish(
            aggregateType = OutboxAggregateType.TRIP,
            aggregateId = 20L,
            eventType = OutboxEventType.TRIP_PARTICIPANTS_ADDED,
            payload = tripParticipantsAddedPayload(recipients = emptyList()),
        )

        assertNull(result)
        verify(outboxEventRepository, never()).save(any(OutboxEvent::class.java))
    }

    @Test
    fun `수신자 없는 계정 삭제 lifecycle 이벤트를 저장한다`() {
        `when`(outboxEventRepository.save(any(OutboxEvent::class.java))).thenAnswer { invocation ->
            invocation.arguments[0] as OutboxEvent
        }

        val event = publisher.publishLifecycle(
            aggregateType = OutboxAggregateType.USER,
            aggregateId = 7L,
            eventType = OutboxEventType.USER_ACCOUNT_DELETED,
            payload = UserAccountDeletedPayload(
                userId = 7L,
                occurredAt = Instant.parse("2026-07-28T12:00:00Z"),
            ),
        )

        assertEquals("USER", event.aggregateType)
        assertEquals("USER_ACCOUNT_DELETED", event.eventType)
        val payload = jacksonObjectMapper().readTree(event.payload)
        assertEquals(1, payload["eventVersion"].intValue())
        assertEquals(7L, payload["userId"].longValue())
        assertEquals("2026-07-28T12:00:00Z", payload["occurredAt"].stringValue())
        assertNull(payload["recipients"])
    }

    @Test
    fun `정산 확정 payload는 중복 제거 후 첫 수신자의 송금 요약을 보존한다`() {
        `when`(outboxEventRepository.save(any(OutboxEvent::class.java))).thenAnswer { invocation ->
            invocation.arguments[0] as OutboxEvent
        }

        publisher.publish(
            aggregateType = OutboxAggregateType.SETTLEMENT,
            aggregateId = 30L,
            eventType = OutboxEventType.SETTLEMENT_CONFIRMED,
            payload = settlementConfirmedPayload(
                recipients = listOf(
                    settlementRecipient(
                        userId = 11L,
                        settlementTransferId = 40L,
                        amount = "12000.00",
                    ),
                    settlementRecipient(
                        userId = 12L,
                        settlementTransferId = 41L,
                        amount = "15000.00",
                    ),
                    settlementRecipient(
                        userId = 11L,
                        settlementTransferId = 42L,
                        amount = "99999.00",
                    ),
                ),
            ),
        )

        val captor = ArgumentCaptor.forClass(OutboxEvent::class.java)
        verify(outboxEventRepository).save(captor.capture())

        val payload = jacksonObjectMapper().readTree(captor.value.payload)
        assertEquals(2, payload["recipients"].size())
        assertEquals(11L, payload["recipients"][0]["userId"].longValue())
        assertEquals(40L, payload["recipients"][0]["transferSummary"]["items"][0]["settlementTransferId"].longValue())
        assertEquals(
            0,
            BigDecimal("12000.00").compareTo(
                payload["recipients"][0]["transferSummary"]["items"][0]["amount"].decimalValue(),
            ),
        )
        assertEquals(12L, payload["recipients"][1]["userId"].longValue())
    }

    @Test
    fun `publish는 기존 도메인 트랜잭션 안에서만 호출되도록 강제한다`() {
        val method = OutboxEventPublisher::class.java.getMethod(
            "publish",
            OutboxAggregateType::class.java,
            java.lang.Long.TYPE,
            OutboxEventType::class.java,
            OutboxNotificationPayload::class.java,
        )

        val transactional = method.getAnnotation(Transactional::class.java)

        assertNotNull(transactional)
        assertEquals(Propagation.MANDATORY, transactional.propagation)

        val lifecycleMethod = OutboxEventPublisher::class.java.getMethod(
            "publishLifecycle",
            OutboxAggregateType::class.java,
            java.lang.Long.TYPE,
            OutboxEventType::class.java,
            OutboxLifecyclePayload::class.java,
        )
        val lifecycleTransactional = lifecycleMethod.getAnnotation(Transactional::class.java)
        assertNotNull(lifecycleTransactional)
        assertEquals(Propagation.MANDATORY, lifecycleTransactional.propagation)
    }

    private fun tripParticipantsAddedPayload(
        recipients: List<DefaultOutboxRecipientPayload>,
    ): TripParticipantsAddedPayload =
        TripParticipantsAddedPayload(
            recipients = recipients,
            actorUserId = 10L,
            tripId = 20L,
            participantIds = listOf(30L),
            tripName = "일본 여행",
            actorDisplayName = "재완",
            occurredAt = Instant.parse("2026-06-22T12:00:00Z"),
        )

    private fun settlementConfirmedPayload(
        recipients: List<SettlementConfirmedRecipientPayload>,
    ): SettlementConfirmedPayload =
        SettlementConfirmedPayload(
            recipients = recipients,
            actorUserId = 10L,
            tripId = 20L,
            settlementId = 30L,
            tripName = "일본 여행",
            occurredAt = Instant.parse("2026-06-22T12:00:00Z"),
        )

    private fun settlementRecipient(
        userId: Long,
        settlementTransferId: Long,
        amount: String,
    ): SettlementConfirmedRecipientPayload =
        SettlementConfirmedRecipientPayload(
            userId = userId,
            transferSummary = SettlementTransferSummaryPayload(
                sendCount = 1,
                totalSendAmount = BigDecimal(amount),
                currency = "KRW",
                items = listOf(
                    SettlementTransferSummaryItemPayload(
                        settlementTransferId = settlementTransferId,
                        receiverParticipantId = 60L,
                        receiverParticipantDisplayName = "동현",
                        amount = BigDecimal(amount),
                    ),
                ),
            ),
        )
}
