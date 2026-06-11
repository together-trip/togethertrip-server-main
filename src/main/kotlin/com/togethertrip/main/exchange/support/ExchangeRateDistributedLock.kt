package com.togethertrip.main.exchange.support

import com.togethertrip.main.exchange.config.ExchangeRateProperties
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ExchangeRateDistributedLock(
    private val redissonClient: RedissonClient,
    private val properties: ExchangeRateProperties,
) {
    private val currentThreadLock = ThreadLocal<RLock>()

    fun <T> runIfAcquired(block: () -> T): T? {
        val lock = redissonClient.getLock(properties.lock.name)
        val acquired = lock.tryLock(
            properties.lock.waitTime.toMillis(),
            properties.lock.leaseTime.toMillis(),
            TimeUnit.MILLISECONDS,
        )

        if (!acquired) {
            logger.info("환율 수집 lock을 획득하지 못해 실행을 건너뜁니다. lockName={}", properties.lock.name)
            return null
        }

        return try {
            block()
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    fun tryAcquireForCurrentThread(): Boolean {
        val lock = redissonClient.getLock(properties.lock.name)
        val acquired = lock.tryLock(
            properties.lock.waitTime.toMillis(),
            properties.lock.leaseTime.toMillis(),
            TimeUnit.MILLISECONDS,
        )

        if (!acquired) {
            logger.info("환율 수집 lock을 획득하지 못해 실행을 건너뜁니다. lockName={}", properties.lock.name)
            return false
        }

        currentThreadLock.set(lock)
        return true
    }

    fun releaseForCurrentThreadIfHeld() {
        val lock = currentThreadLock.get() ?: return
        try {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        } finally {
            currentThreadLock.remove()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ExchangeRateDistributedLock::class.java)
    }
}
