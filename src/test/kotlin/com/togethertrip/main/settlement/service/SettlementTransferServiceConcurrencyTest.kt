package com.togethertrip.main.settlement.service

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.user.domain.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@MainIntegrationTest
class SettlementTransferServiceConcurrencyTest @Autowired constructor(
    private val service: SettlementTransferService,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
) {

    @Test
    fun `송금자와 수금자 중복 확인 16건씩은 상태와 Outbox를 한 번씩만 전이한다`() {
        val fixture = createFixture()

        val senderStatuses = runConcurrently(16) {
            service.confirmAsSender(fixture.senderUserId, fixture.tripId, fixture.transferId).status
        }
        assertTrue(senderStatuses.all { it == SettlementTransferStatus.SENDER_CONFIRMED })
        assertEquals(1L, countOutbox(fixture.transferId, "SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER"))

        val receiverStatuses = runConcurrently(16) {
            service.confirmAsReceiver(fixture.receiverUserId, fixture.tripId, fixture.transferId).status
        }
        assertTrue(receiverStatuses.all { it == SettlementTransferStatus.COMPLETED })
        assertEquals(1L, countOutbox(fixture.transferId, "SETTLEMENT_TRANSFER_COMPLETED"))
        assertEquals(TripSettlementStatus.SETTLED, readTripStatus(fixture.tripId))
    }

    private fun <T> runConcurrently(count: Int, block: () -> T): List<T> {
        val executor = Executors.newFixedThreadPool(count)
        val ready = CountDownLatch(count)
        val start = CountDownLatch(1)
        return try {
            val futures = (0 until count).map {
                executor.submit<T> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    block()
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            futures.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun createFixture(): FixtureIds {
        return inTransaction {
            val senderUser = persist(User(nickname = "송금 확인 사용자"))
            val receiverUser = persist(User(nickname = "수금 확인 사용자"))
            val trip = persist(
                Trip(
                    ownerUser = senderUser,
                    title = "송금 확인 동시성 여행",
                    defaultCurrency = "KRW",
                    settlementStatus = TripSettlementStatus.IN_PROGRESS,
                )
            )
            val sender = persist(
                TripParticipant(
                    trip = trip,
                    user = senderUser,
                    displayName = "송금자",
                    participantRole = TripParticipantRole.LEADER,
                    participantStatus = TripParticipantStatus.ACTIVE,
                )
            )
            val receiver = persist(
                TripParticipant(
                    trip = trip,
                    user = receiverUser,
                    displayName = "수금자",
                    participantRole = TripParticipantRole.MEMBER,
                    participantStatus = TripParticipantStatus.ACTIVE,
                )
            )
            val settlement = persist(
                Settlement(
                    trip = trip,
                    status = SettlementStatus.CONFIRMED,
                    tripExpenseVersion = 1,
                    calculationVersion = "settlement-v1",
                    baseCurrency = "KRW",
                    totalExpenseAmount = BigDecimal("1000.00"),
                    totalShareAmount = BigDecimal("1000.00"),
                    snapshotPayload = "{}",
                    confirmedAt = Instant.parse("2026-07-09T00:00:00Z"),
                    confirmedBy = senderUser,
                )
            )
            val transfer = persist(
                SettlementTransfer(
                    settlement = settlement,
                    sender = sender,
                    receiver = receiver,
                    amount = BigDecimal("1000.00"),
                    currency = "KRW",
                    status = SettlementTransferStatus.PENDING,
                )
            )
            entityManager.flush()
            FixtureIds(senderUser.id, receiverUser.id, trip.id, transfer.id)
        }
    }

    private fun countOutbox(transferId: Long, eventType: String): Long {
        return inTransaction {
            (entityManager.createNativeQuery(
                """
                select count(*)
                from outbox_events
                where aggregate_id = :transferId
                  and event_type = :eventType
                """.trimIndent()
            )
                .setParameter("transferId", transferId)
                .setParameter("eventType", eventType)
                .singleResult as Number).toLong()
        }
    }

    private fun readTripStatus(tripId: Long): TripSettlementStatus {
        return inTransaction {
            TripSettlementStatus.valueOf(
                entityManager.createNativeQuery("select settlement_status from trips where id = :tripId")
                    .setParameter("tripId", tripId)
                    .singleResult as String
            )
        }
    }

    private fun <T : Any> persist(entity: T): T {
        entityManager.persist(entity)
        return entity
    }

    private fun <T> inTransaction(block: () -> T): T {
        return requireNotNull(transactionTemplate.execute { block() })
    }

    private data class FixtureIds(
        val senderUserId: Long,
        val receiverUserId: Long,
        val tripId: Long,
        val transferId: Long,
    )
}
