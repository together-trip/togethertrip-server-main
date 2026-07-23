package com.togethertrip.main.settlement.repository

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
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
class SettlementTransferRepositoryConcurrencyTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val repository: SettlementTransferRepository,
    private val settlementRepository: SettlementRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    @Test
    fun `동일 송금자 확인 16건이 동시에 시작해도 DB 갱신은 한 번만 성공한다`() {
        val fixture = createFixture()
        val contenderCount = 16
        val executor = Executors.newFixedThreadPool(contenderCount)
        val ready = CountDownLatch(contenderCount)
        val start = CountDownLatch(1)
        val confirmedTimes = (0 until contenderCount).map {
            Instant.parse("2026-07-05T00:00:00Z").plusSeconds(it.toLong())
        }

        try {
            val futures = confirmedTimes.map { confirmedAt ->
                executor.submit<Int> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    inTransaction {
                        repository.confirmAsSenderIfNeeded(
                            fixture.transferId,
                            fixture.tripId,
                            fixture.senderId,
                            confirmedAt,
                        )
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            assertEquals(1, futures.sumOf { it.get(10, TimeUnit.SECONDS) })
            val finalState = readState(fixture.transferId)
            assertEquals("SENDER_CONFIRMED", finalState.status)
            assertTrue(finalState.senderConfirmedAt in confirmedTimes)
            assertEquals(null, finalState.completedAt)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `송금자와 수금자 확인이 동시에 시작해도 20회 모두 COMPLETED로 수렴한다`() {
        val executor = Executors.newFixedThreadPool(2)

        try {
            repeat(20) { round ->
                val fixture = createFixture()
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val senderAt = Instant.parse("2026-07-06T00:00:00Z").plusSeconds((round * 2).toLong())
                val receiverAt = senderAt.plusSeconds(1)
                val senderFuture = executor.submit<Int> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    inTransaction {
                        repository.confirmAsSenderIfNeeded(
                            fixture.transferId,
                            fixture.tripId,
                            fixture.senderId,
                            senderAt,
                        )
                    }
                }
                val receiverFuture = executor.submit<Int> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    inTransaction {
                        repository.confirmAsReceiverIfNeeded(
                            fixture.transferId,
                            fixture.tripId,
                            fixture.receiverId,
                            receiverAt,
                        )
                    }
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS))
                start.countDown()

                assertEquals(1, senderFuture.get(10, TimeUnit.SECONDS), "round=$round sender")
                assertEquals(1, receiverFuture.get(10, TimeUnit.SECONDS), "round=$round receiver")
                val finalState = readState(fixture.transferId)
                assertEquals("COMPLETED", finalState.status, "round=$round")
                assertEquals(senderAt, finalState.senderConfirmedAt, "round=$round")
                assertEquals(receiverAt, finalState.receiverConfirmedAt, "round=$round")
                assertTrue(finalState.completedAt == senderAt || finalState.completedAt == receiverAt, "round=$round")
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `서로 다른 공유 토큰 16개가 동시에 발급되어도 하나만 저장된다`() {
        val fixture = createFixture()
        val contenderCount = 16
        val executor = Executors.newFixedThreadPool(contenderCount)
        val ready = CountDownLatch(contenderCount)
        val start = CountDownLatch(1)
        val tokens = (0 until contenderCount).map { "concurrent-share-token-$it" }

        try {
            val futures = tokens.mapIndexed { index, token ->
                executor.submit<Int> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    inTransaction {
                        settlementRepository.updateShareTokenIfAbsent(
                            settlementId = fixture.settlementId,
                            shareToken = token,
                            updatedAt = Instant.parse("2026-07-07T00:00:00Z").plusSeconds(index.toLong()),
                        )
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            assertEquals(1, futures.sumOf { it.get(10, TimeUnit.SECONDS) })
            val storedToken = inTransaction {
                requireNotNull(settlementRepository.findById(fixture.settlementId).orElseThrow().shareToken)
            }
            assertTrue(storedToken in tokens)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun createFixture(): FixtureIds {
        return inTransaction {
            val senderUser = persist(User(nickname = "동시 송금자"))
            val receiverUser = persist(User(nickname = "동시 수금자"))
            val trip = persist(
                Trip(
                    ownerUser = senderUser,
                    title = "정산 동시성 테스트 여행",
                    defaultCurrency = "KRW",
                )
            )
            val sender = persist(
                TripParticipant(
                    trip = trip,
                    user = senderUser,
                    displayName = "동시 송금자",
                    participantRole = TripParticipantRole.LEADER,
                    participantStatus = TripParticipantStatus.ACTIVE,
                )
            )
            val receiver = persist(
                TripParticipant(
                    trip = trip,
                    user = receiverUser,
                    displayName = "동시 수금자",
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
                    confirmedAt = Instant.parse("2026-07-05T00:00:00Z"),
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
            FixtureIds(trip.id, settlement.id, sender.id, receiver.id, transfer.id)
        }
    }

    private fun readState(transferId: Long): TransferState {
        return inTransaction {
            val row = requireNotNull(repository.findTransferRowById(transferId))
            TransferState(
                status = row.getStatus(),
                senderConfirmedAt = row.getSenderConfirmedAt(),
                receiverConfirmedAt = row.getReceiverConfirmedAt(),
                completedAt = row.getCompletedAt(),
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
        val tripId: Long,
        val settlementId: Long,
        val senderId: Long,
        val receiverId: Long,
        val transferId: Long,
    )

    private data class TransferState(
        val status: String,
        val senderConfirmedAt: Instant?,
        val receiverConfirmedAt: Instant?,
        val completedAt: Instant?,
    )
}
