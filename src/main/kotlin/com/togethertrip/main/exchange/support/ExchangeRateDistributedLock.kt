package com.togethertrip.main.exchange.support

import com.togethertrip.main.exchange.config.ExchangeRateProperties
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ExchangeRateDistributedLock(
    private val redissonClient: RedissonClient,
    private val properties: ExchangeRateProperties,
) {

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

    companion object {
        private val logger = LoggerFactory.getLogger(ExchangeRateDistributedLock::class.java)
    }
}
