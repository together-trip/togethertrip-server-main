package com.togethertrip.main.exchange.support

import com.togethertrip.main.global.config.MainIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@MainIntegrationTest
class ExchangeRateDistributedLockIntegrationTest @Autowired constructor(
    private val lock: ExchangeRateDistributedLock,
) {

    @Test
    fun `환율 수집 lock 16개가 동시에 경쟁하면 하나의 block만 실행한다`() {
        val contenderCount = 16
        val executor = Executors.newFixedThreadPool(contenderCount)
        val ready = CountDownLatch(contenderCount)
        val start = CountDownLatch(1)
        val winnerEntered = CountDownLatch(1)
        val releaseWinner = CountDownLatch(1)
        val blockExecutions = AtomicInteger()

        try {
            val futures = (0 until contenderCount).map {
                executor.submit<Int?> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    lock.runIfAcquired {
                        blockExecutions.incrementAndGet()
                        winnerEntered.countDown()
                        releaseWinner.await(10, TimeUnit.SECONDS)
                        1
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(winnerEntered.await(10, TimeUnit.SECONDS))
            releaseWinner.countDown()

            val outcomes = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, blockExecutions.get())
            assertEquals(1, outcomes.count { it == 1 })
            assertEquals(15, outcomes.count { it == null })
        } finally {
            releaseWinner.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `lock block이 예외로 끝나도 다음 호출이 lock을 획득한다`() {
        assertFailsWith<IllegalStateException> {
            lock.runIfAcquired<Unit> { error("expected failure") }
        }

        assertEquals("acquired-after-failure", lock.runIfAcquired { "acquired-after-failure" })
    }

    @Test
    fun `수동 acquire는 다른 thread를 차단하고 소유 thread의 release 이후 재획득된다`() {
        assertTrue(lock.tryAcquireForCurrentThread())
        val executor = Executors.newSingleThreadExecutor()

        try {
            assertFalse(executor.submit<Boolean> { lock.tryAcquireForCurrentThread() }.get(10, TimeUnit.SECONDS))
            lock.releaseForCurrentThreadIfHeld()
            assertTrue(
                executor.submit<Boolean> {
                    val acquired = lock.tryAcquireForCurrentThread()
                    lock.releaseForCurrentThreadIfHeld()
                    acquired
                }.get(10, TimeUnit.SECONDS)
            )
        } finally {
            lock.releaseForCurrentThreadIfHeld()
            executor.shutdownNow()
        }
    }
}
