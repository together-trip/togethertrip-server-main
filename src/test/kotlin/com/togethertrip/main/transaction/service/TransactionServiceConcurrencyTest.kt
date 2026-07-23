package com.togethertrip.main.transaction.service

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.transaction.dto.request.CreateTransactionRequest
import com.togethertrip.main.transaction.dto.request.TransactionPaymentInput
import com.togethertrip.main.transaction.dto.request.TransactionShareInput
import com.togethertrip.main.transaction.dto.request.UpdateTransactionRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionPaymentsRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionSharesRequest
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.transaction.exception.TransactionErrorCode
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@MainIntegrationTest
class TransactionServiceConcurrencyTest @Autowired constructor(
    private val transactionService: TransactionService,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
) {

    @Test
    fun `거래 이벤트 원장은 aggregate version 순서로 응답한다`() {
        val fixture = createFixture(301)
        val transactionId = createInitialTransaction(fixture)
        transactionService.updateTransaction(
            fixture.userId,
            fixture.tripId,
            transactionId,
            updateRequest(fixture.participantId, BigDecimal("130.00")),
        )

        val events = transactionService.getTransactionEvents(
            fixture.userId,
            fixture.tripId,
            transactionId,
        )

        assertEquals(listOf("CREATED", "UPDATED"), events.map { it.eventType })
        assertEquals(listOf(1L, 2L), events.map { it.aggregateVersion })
        assertTrue(events.all { it.transactionId == transactionId && it.tripId == fixture.tripId })
    }

    @Test
    fun `결제자와 분담자 부분 수정은 기존 배분을 보존하며 각각 새 이벤트를 생성한다`() {
        val fixture = createFixture(300)
        val transactionId = createInitialTransaction(fixture)

        val paymentResponse = transactionService.updateTransactionPayments(
            fixture.userId,
            fixture.tripId,
            transactionId,
            UpdateTransactionPaymentsRequest(
                payments = listOf(TransactionPaymentInput(fixture.participantId, BigDecimal("100.00"))),
            ),
        )
        val shareResponse = transactionService.updateTransactionShares(
            fixture.userId,
            fixture.tripId,
            transactionId,
            UpdateTransactionSharesRequest(
                shares = listOf(TransactionShareInput(fixture.participantId, BigDecimal("100.00"))),
            ),
        )

        assertEquals(BigDecimal("100.00"), paymentResponse.payments.single().amount)
        assertEquals(BigDecimal("100.00"), paymentResponse.shares.single().shareAmount)
        assertEquals(BigDecimal("100.00"), shareResponse.payments.single().amount)
        assertEquals(BigDecimal("100.00"), shareResponse.shares.single().shareAmount)
        assertUpdatedState(
            fixture = fixture,
            transactionId = transactionId,
            expectedAmounts = listOf(BigDecimal("100.00")),
            round = 300,
            expectedTransactionVersion = 0L,
        )
    }

    @Test
    fun `같은 여행의 거래 두 건을 동시에 생성해도 모두 반영되고 집계가 수렴한다`() {
        val executor = Executors.newFixedThreadPool(2)

        try {
            repeat(10) { round ->
                val fixture = createFixture(round)
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val futures = (0 until 2).map {
                    executor.submit<Result<Unit>> {
                        ready.countDown()
                        start.await(10, TimeUnit.SECONDS)
                        runCatching {
                            transactionService.createTransaction(
                                userId = fixture.userId,
                                tripId = fixture.tripId,
                                request = request(fixture.participantId),
                            )
                            Unit
                        }
                    }
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS), "round=$round")
                start.countDown()

                val outcomes = futures.map { it.get(10, TimeUnit.SECONDS) }
                assertEquals(2, outcomes.count(Result<Unit>::isSuccess), failureMessage(round, outcomes))
                assertFinalState(fixture.tripId, fixture.participantId, round)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `같은 거래를 동시에 두 번 수정해도 모두 반영되고 마지막 집계로 수렴한다`() {
        val executor = Executors.newFixedThreadPool(2)

        try {
            repeat(10) { round ->
                val fixture = createFixture(100 + round)
                val transactionId = createInitialTransaction(fixture)
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val amounts = listOf(BigDecimal("110.00"), BigDecimal("120.00"))
                val futures = amounts.map { amount ->
                    executor.submit<Result<Unit>> {
                        ready.countDown()
                        start.await(10, TimeUnit.SECONDS)
                        runCatching {
                            transactionService.updateTransaction(
                                fixture.userId,
                                fixture.tripId,
                                transactionId,
                                updateRequest(fixture.participantId, amount),
                            )
                            Unit
                        }
                    }
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS), "round=$round")
                start.countDown()

                val outcomes = futures.map { it.get(10, TimeUnit.SECONDS) }
                assertEquals(2, outcomes.count(Result<Unit>::isSuccess), failureMessage(round, outcomes))
                assertUpdatedState(fixture, transactionId, amounts, round)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `거래 수정과 삭제가 경합해도 삭제 상태와 0원 집계로 수렴한다`() {
        val executor = Executors.newFixedThreadPool(2)

        try {
            repeat(10) { round ->
                val fixture = createFixture(200 + round)
                val transactionId = createInitialTransaction(fixture)
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val update = executor.submit<String> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    try {
                        transactionService.updateTransaction(
                            fixture.userId,
                            fixture.tripId,
                            transactionId,
                            updateRequest(fixture.participantId, BigDecimal("150.00")),
                        )
                        "UPDATED"
                    } catch (exception: BusinessException) {
                        if (exception.errorCode == TransactionErrorCode.TRANSACTION_ALREADY_VOIDED) "ALREADY_VOIDED" else throw exception
                    }
                }
                val delete = executor.submit<String> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    transactionService.deleteTransaction(fixture.userId, fixture.tripId, transactionId)
                    "DELETED"
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS), "round=$round")
                start.countDown()

                val outcomes = listOf(update.get(10, TimeUnit.SECONDS), delete.get(10, TimeUnit.SECONDS))
                assertEquals(1, outcomes.count { it == "DELETED" }, "round=$round outcomes=$outcomes")
                assertTrue(outcomes.first() == "UPDATED" || outcomes.first() == "ALREADY_VOIDED", "round=$round outcomes=$outcomes")
                assertVoidedState(fixture, transactionId, outcomes.first() == "UPDATED", round)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun createFixture(round: Int): Fixture {
        return inTransaction {
            val user = User(nickname = "거래 경합 방장 $round")
            entityManager.persist(user)
            val trip = Trip(user, "거래 경합 여행 $round", "KRW")
            entityManager.persist(trip)
            val participant = TripParticipant(
                trip = trip,
                user = user,
                displayName = user.nickname,
                participantRole = TripParticipantRole.LEADER,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
            entityManager.persist(participant)
            entityManager.flush()
            Fixture(user.id, trip.id, participant.id)
        }
    }

    private fun request(participantId: Long): CreateTransactionRequest {
        return CreateTransactionRequest(
            amount = BigDecimal("100.00"),
            currency = "KRW",
            payments = listOf(TransactionPaymentInput(participantId, BigDecimal("100.00"))),
            shares = listOf(TransactionShareInput(participantId, BigDecimal("100.00"))),
        )
    }

    private fun updateRequest(participantId: Long, amount: BigDecimal): UpdateTransactionRequest {
        return UpdateTransactionRequest(
            amount = amount,
            currency = "KRW",
            payments = listOf(TransactionPaymentInput(participantId, amount)),
            shares = listOf(TransactionShareInput(participantId, amount)),
        )
    }

    private fun createInitialTransaction(fixture: Fixture): Long {
        return transactionService.createTransaction(
            fixture.userId,
            fixture.tripId,
            request(fixture.participantId),
        ).summary.id
    }

    private fun assertFinalState(tripId: Long, participantId: Long, round: Int) {
        inTransaction {
            val counts = entityManager.createNativeQuery(
                """
                select
                    (select count(*) from transactions where trip_id = :tripId and deleted_at is null),
                    (select count(*) from transaction_events where trip_id = :tripId and deleted_at is null),
                    (select expense_version from trips where id = :tripId),
                    (select paid_base_amount from trip_participant_balance_summaries
                        where trip_id = :tripId and trip_participant_id = :participantId),
                    (select share_base_amount from trip_participant_balance_summaries
                        where trip_id = :tripId and trip_participant_id = :participantId)
                """.trimIndent()
            )
                .setParameter("tripId", tripId)
                .setParameter("participantId", participantId)
                .singleResult as Array<*>

            assertEquals(2L, (counts[0] as Number).toLong(), "transaction round=$round")
            assertEquals(2L, (counts[1] as Number).toLong(), "event round=$round")
            assertEquals(2L, (counts[2] as Number).toLong(), "expense version round=$round")
            assertEquals(0, BigDecimal(counts[3].toString()).compareTo(BigDecimal("200.00")), "paid round=$round")
            assertEquals(0, BigDecimal(counts[4].toString()).compareTo(BigDecimal("200.00")), "share round=$round")
        }
    }

    private fun assertUpdatedState(
        fixture: Fixture,
        transactionId: Long,
        expectedAmounts: List<BigDecimal>,
        round: Int,
        expectedTransactionVersion: Long = 2L,
    ) {
        inTransaction {
            val row = entityManager.createNativeQuery(
                """
                select t.amount, t.version,
                    (select count(*) from transaction_events where transaction_id = :transactionId and deleted_at is null),
                    (select expense_version from trips where id = :tripId),
                    (select count(*) from transaction_payments where transaction_id = :transactionId and deleted_at is null),
                    (select count(*) from transaction_shares where transaction_id = :transactionId and deleted_at is null),
                    (select paid_base_amount from trip_participant_balance_summaries where trip_id = :tripId and trip_participant_id = :participantId),
                    (select share_base_amount from trip_participant_balance_summaries where trip_id = :tripId and trip_participant_id = :participantId)
                from transactions t where t.id = :transactionId
                """.trimIndent()
            )
                .setParameter("transactionId", transactionId)
                .setParameter("tripId", fixture.tripId)
                .setParameter("participantId", fixture.participantId)
                .singleResult as Array<*>
            val finalAmount = BigDecimal(row[0].toString())
            assertTrue(expectedAmounts.any { it.compareTo(finalAmount) == 0 }, "amount round=$round")
            assertEquals(expectedTransactionVersion, (row[1] as Number).toLong(), "transaction version round=$round")
            assertEquals(3L, (row[2] as Number).toLong(), "events round=$round")
            assertEquals(3L, (row[3] as Number).toLong(), "expense version round=$round")
            assertEquals(1L, (row[4] as Number).toLong(), "active payments round=$round")
            assertEquals(1L, (row[5] as Number).toLong(), "active shares round=$round")
            assertEquals(0, BigDecimal(row[6].toString()).compareTo(finalAmount), "paid round=$round")
            assertEquals(0, BigDecimal(row[7].toString()).compareTo(finalAmount), "share round=$round")
        }
    }

    private fun assertVoidedState(fixture: Fixture, transactionId: Long, updateSucceeded: Boolean, round: Int) {
        inTransaction {
            val row = entityManager.createNativeQuery(
                """
                select t.status,
                    (select count(*) from transaction_events where transaction_id = :transactionId and deleted_at is null),
                    (select expense_version from trips where id = :tripId),
                    (select paid_base_amount from trip_participant_balance_summaries where trip_id = :tripId and trip_participant_id = :participantId),
                    (select share_base_amount from trip_participant_balance_summaries where trip_id = :tripId and trip_participant_id = :participantId)
                from transactions t where t.id = :transactionId
                """.trimIndent()
            )
                .setParameter("transactionId", transactionId)
                .setParameter("tripId", fixture.tripId)
                .setParameter("participantId", fixture.participantId)
                .singleResult as Array<*>
            assertEquals(TransactionStatus.VOIDED.name, row[0], "status round=$round")
            val expectedVersion = if (updateSucceeded) 3L else 2L
            assertEquals(expectedVersion, (row[1] as Number).toLong(), "events round=$round")
            assertEquals(expectedVersion, (row[2] as Number).toLong(), "expense version round=$round")
            assertEquals(0, BigDecimal(row[3].toString()).compareTo(BigDecimal.ZERO), "paid round=$round")
            assertEquals(0, BigDecimal(row[4].toString()).compareTo(BigDecimal.ZERO), "share round=$round")
        }
    }

    private fun failureMessage(round: Int, outcomes: List<Result<Unit>>): String {
        val failures = outcomes.mapNotNull { it.exceptionOrNull() }
            .joinToString { exception ->
                generateSequence(exception) { it.cause }
                    .joinToString(" <- ") { it::class.simpleName ?: "unknown" }
            }
        return "round=$round failures=$failures"
    }

    private fun <T> inTransaction(block: () -> T): T {
        return requireNotNull(transactionTemplate.execute { block() })
    }

    private data class Fixture(
        val userId: Long,
        val tripId: Long,
        val participantId: Long,
    )
}
