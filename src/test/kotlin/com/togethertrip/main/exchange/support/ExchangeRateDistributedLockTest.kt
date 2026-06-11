package com.togethertrip.main.exchange.support

import com.togethertrip.main.exchange.config.ExchangeRateProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExchangeRateDistributedLockTest {

    private lateinit var redissonClient: RedissonClient
    private lateinit var lock: RLock
    private lateinit var properties: ExchangeRateProperties
    private lateinit var distributedLock: ExchangeRateDistributedLock

    @BeforeEach
    fun setUp() {
        redissonClient = mock(RedissonClient::class.java)
        lock = mock(RLock::class.java)
        properties = ExchangeRateProperties().apply {
            this.lock.name = "exchange-rate:test-lock"
            this.lock.waitTime = Duration.ZERO
            this.lock.leaseTime = Duration.ofMinutes(5)
        }
        distributedLock = ExchangeRateDistributedLock(redissonClient, properties)

        `when`(redissonClient.getLock("exchange-rate:test-lock")).thenReturn(lock)
    }

    @Test
    fun `lock 획득에 성공하면 block을 실행하고 unlock 한다`() {
        `when`(
            lock.tryLock(
                0L,
                Duration.ofMinutes(5).toMillis(),
                TimeUnit.MILLISECONDS,
            )
        ).thenReturn(true)
        `when`(lock.isHeldByCurrentThread).thenReturn(true)

        val result = distributedLock.runIfAcquired { "done" }

        assertEquals("done", result)
        verify(lock).unlock()
    }

    @Test
    fun `lock 획득에 실패하면 block을 실행하지 않는다`() {
        `when`(
            lock.tryLock(
                0L,
                Duration.ofMinutes(5).toMillis(),
                TimeUnit.MILLISECONDS,
            )
        ).thenReturn(false)

        val result = distributedLock.runIfAcquired { "done" }

        assertNull(result)
        verify(lock, never()).unlock()
    }
}
