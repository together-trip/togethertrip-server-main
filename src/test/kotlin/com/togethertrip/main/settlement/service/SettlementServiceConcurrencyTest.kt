package com.togethertrip.main.settlement.service

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
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
class SettlementServiceConcurrencyTest @Autowired constructor(
    private val settlementService: SettlementService,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
) {

    @Test
    fun `같은 여행 정산 확정 2건이 동시에 시작해도 10회 모두 snapshot transfer outbox가 하나씩 생성된다`() {
        val executor = Executors.newFixedThreadPool(2)

        try {
            repeat(10) { round ->
                val fixture = createFixture(round)
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val futures = (0 until 2).map {
                    executor.submit<String> {
                        ready.countDown()
                        start.await(10, TimeUnit.SECONDS)
                        try {
                            settlementService.confirmSettlement(fixture.ownerUserId, fixture.tripId)
                            "SUCCESS"
                        } catch (exception: BusinessException) {
                            (exception.errorCode as SettlementErrorCode).name
                        }
                    }
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS))
                start.countDown()

                val outcomes = futures.map { it.get(10, TimeUnit.SECONDS) }
                assertEquals(1, outcomes.count { it == "SUCCESS" }, "round=$round")
                assertEquals(
                    1,
                    outcomes.count { it == SettlementErrorCode.SETTLEMENT_ALREADY_CONFIRMED.name },
                    "round=$round",
                )
                assertEquals(1L, countConfirmedSettlements(fixture.tripId), "round=$round")
                assertEquals(1L, countTransfers(fixture.tripId), "round=$round")
                assertEquals(1L, countConfirmedOutboxEvents(fixture.tripId), "round=$round")
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun createFixture(round: Int): FixtureIds {
        return inTransaction {
            val owner = persist(User(nickname = "동시 정산 방장 $round"))
            val member = persist(User(nickname = "동시 정산 회원 $round"))
            val trip = persist(
                Trip(
                    ownerUser = owner,
                    title = "동시 정산 여행 $round",
                    defaultCurrency = "KRW",
                )
            )
            val ownerParticipant = persist(
                TripParticipant(
                    trip = trip,
                    user = owner,
                    displayName = "방장",
                    participantRole = TripParticipantRole.LEADER,
                    participantStatus = TripParticipantStatus.ACTIVE,
                )
            )
            val memberParticipant = persist(
                TripParticipant(
                    trip = trip,
                    user = member,
                    displayName = "회원",
                    participantRole = TripParticipantRole.MEMBER,
                    participantStatus = TripParticipantStatus.ACTIVE,
                )
            )
            val transaction = persist(
                Transaction(
                    trip = trip,
                    createdBy = owner,
                    transactionType = TransactionType.EXPENSE,
                    amount = BigDecimal("10000.00"),
                    currency = "KRW",
                    exchangeRate = BigDecimal("1.000000"),
                    baseCurrency = "KRW",
                    baseAmount = BigDecimal("10000.00"),
                    status = TransactionStatus.ACTIVE,
                )
            )
            persist(
                TransactionPayment(
                    transaction = transaction,
                    tripParticipant = ownerParticipant,
                    amount = BigDecimal("10000.00"),
                    currency = "KRW",
                    exchangeRate = BigDecimal("1.000000"),
                    baseCurrency = "KRW",
                    baseAmount = BigDecimal("10000.00"),
                )
            )
            persist(
                TransactionShare(
                    transaction = transaction,
                    tripParticipant = ownerParticipant,
                    shareAmount = BigDecimal("5000.00"),
                    currency = "KRW",
                    exchangeRate = BigDecimal("1.000000"),
                    baseCurrency = "KRW",
                    baseShareAmount = BigDecimal("5000.00"),
                )
            )
            persist(
                TransactionShare(
                    transaction = transaction,
                    tripParticipant = memberParticipant,
                    shareAmount = BigDecimal("5000.00"),
                    currency = "KRW",
                    exchangeRate = BigDecimal("1.000000"),
                    baseCurrency = "KRW",
                    baseShareAmount = BigDecimal("5000.00"),
                )
            )
            entityManager.flush()
            FixtureIds(owner.id, trip.id)
        }
    }

    private fun countConfirmedSettlements(tripId: Long): Long = count(
        "select count(*) from settlements where trip_id = :tripId and status = 'CONFIRMED'",
        tripId,
    )

    private fun countTransfers(tripId: Long): Long = count(
        """
        select count(*)
        from settlement_transfers transfer
        join settlements settlement on settlement.id = transfer.settlement_id
        where settlement.trip_id = :tripId
        """.trimIndent(),
        tripId,
    )

    private fun countConfirmedOutboxEvents(tripId: Long): Long = count(
        """
        select count(*)
        from outbox_events
        where event_type = 'SETTLEMENT_CONFIRMED'
          and payload ->> 'tripId' = cast(:tripId as text)
        """.trimIndent(),
        tripId,
    )

    private fun count(sql: String, tripId: Long): Long {
        return inTransaction {
            (entityManager.createNativeQuery(sql)
                .setParameter("tripId", tripId)
                .singleResult as Number).toLong()
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
        val ownerUserId: Long,
        val tripId: Long,
    )
}
