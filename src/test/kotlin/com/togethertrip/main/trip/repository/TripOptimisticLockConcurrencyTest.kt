package com.togethertrip.main.trip.repository

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.user.domain.User
import jakarta.persistence.EntityManager
import jakarta.persistence.OptimisticLockException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@MainIntegrationTest
class TripOptimisticLockConcurrencyTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
) {

    @Test
    fun `같은 trip version을 읽은 두 transaction은 20회 모두 하나만 expense version을 갱신한다`() {
        val executor = Executors.newFixedThreadPool(2)

        try {
            repeat(20) { round ->
                val tripId = createTrip(round)
                val loaded = CountDownLatch(2)
                val mutate = CountDownLatch(1)
                val futures = (0 until 2).map {
                    executor.submit<String> {
                        try {
                            inTransaction {
                                val trip = entityManager.find(Trip::class.java, tripId)
                                loaded.countDown()
                                mutate.await(10, TimeUnit.SECONDS)
                                trip.advanceExpenseVersion()
                                entityManager.flush()
                            }
                            "SUCCESS"
                        } catch (exception: Exception) {
                            if (exception.hasOptimisticLockCause()) "CONFLICT" else throw exception
                        }
                    }
                }
                assertTrue(loaded.await(10, TimeUnit.SECONDS))
                mutate.countDown()

                val outcomes = futures.map { it.get(10, TimeUnit.SECONDS) }
                assertEquals(1, outcomes.count { it == "SUCCESS" }, "round=$round")
                assertEquals(1, outcomes.count { it == "CONFLICT" }, "round=$round")
                val versions = readVersions(tripId)
                assertEquals(1L, versions.expenseVersion, "round=$round")
                assertEquals(1L, versions.entityVersion, "round=$round")
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun createTrip(round: Int): Long {
        return inTransaction {
            val owner = User(nickname = "낙관적 잠금 방장 $round")
            entityManager.persist(owner)
            val trip = Trip(
                ownerUser = owner,
                title = "낙관적 잠금 여행 $round",
                defaultCurrency = "KRW",
            )
            entityManager.persist(trip)
            entityManager.flush()
            trip.id
        }
    }

    private fun readVersions(tripId: Long): Versions {
        return inTransaction {
            val row = entityManager.createNativeQuery(
                "select expense_version, version from trips where id = :tripId"
            )
                .setParameter("tripId", tripId)
                .singleResult as Array<*>
            Versions(
                expenseVersion = (row[0] as Number).toLong(),
                entityVersion = (row[1] as Number).toLong(),
            )
        }
    }

    private fun Throwable.hasOptimisticLockCause(): Boolean {
        return generateSequence(this) { it.cause }
            .any { it is OptimisticLockException || it is OptimisticLockingFailureException }
    }

    private fun <T> inTransaction(block: () -> T): T {
        return requireNotNull(transactionTemplate.execute { block() })
    }

    private data class Versions(
        val expenseVersion: Long,
        val entityVersion: Long,
    )
}
