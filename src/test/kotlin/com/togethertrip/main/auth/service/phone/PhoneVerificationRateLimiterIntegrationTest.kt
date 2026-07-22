package com.togethertrip.main.auth.service.phone

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.exception.BusinessException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@MainIntegrationTest
class PhoneVerificationRateLimiterIntegrationTest @Autowired constructor(
    private val rateLimiter: PhoneVerificationRateLimiter,
    private val redisTemplate: StringRedisTemplate,
) {

    private val hashes = mutableSetOf<String>()

    @AfterEach
    fun cleanRedisKeys() {
        hashes.flatMap { hash -> redisTemplate.keys("auth:phone-verification:*$hash*") }
            .takeIf { it.isNotEmpty() }
            ?.let(redisTemplate::delete)
    }

    @Test
    fun `첫 요청은 rate와 daily key에 TTL을 만들고 즉시 재요청은 거부한다`() {
        val hash = track("sequential-rate-limit")

        rateLimiter.validate(hash)
        val exception = assertFailsWith<BusinessException> { rateLimiter.validate(hash) }

        assertEquals(AuthErrorCode.PHONE_VERIFICATION_REQUEST_TOO_SOON, exception.errorCode)
        val rateTtl = redisTemplate.getExpire(rateKey(hash), TimeUnit.SECONDS)
        assertTrue(rateTtl in 58..60)
        assertEquals("1", redisTemplate.opsForValue().get(dailyKey(hash)))
        val dailyTtl = redisTemplate.getExpire(dailyKey(hash), TimeUnit.SECONDS)
        assertTrue(dailyTtl in 172_798..172_800)
    }

    @Test
    fun `같은 전화번호 인증 요청 16건이 동시에 시작해도 하나만 허용한다`() {
        val hash = track("concurrent-rate-limit")
        val contenderCount = 16
        val executor = Executors.newFixedThreadPool(contenderCount)
        val ready = CountDownLatch(contenderCount)
        val start = CountDownLatch(1)

        try {
            val futures = (0 until contenderCount).map {
                executor.submit<AuthErrorCode?> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    try {
                        rateLimiter.validate(hash)
                        null
                    } catch (exception: BusinessException) {
                        exception.errorCode as AuthErrorCode
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val outcomes = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, outcomes.count { it == null })
            assertEquals(
                15,
                outcomes.count { it == AuthErrorCode.PHONE_VERIFICATION_REQUEST_TOO_SOON },
            )
            assertEquals("1", redisTemplate.opsForValue().get(dailyKey(hash)))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `재요청 간격이 지난 요청도 일일 5회를 초과하면 거부한다`() {
        val hash = track("daily-rate-limit")

        repeat(5) {
            rateLimiter.validate(hash)
            redisTemplate.delete(rateKey(hash))
        }
        val exception = assertFailsWith<BusinessException> { rateLimiter.validate(hash) }

        assertEquals(AuthErrorCode.PHONE_VERIFICATION_DAILY_LIMIT_EXCEEDED, exception.errorCode)
        assertEquals("6", redisTemplate.opsForValue().get(dailyKey(hash)))
        assertTrue(redisTemplate.getExpire(dailyKey(hash), TimeUnit.SECONDS) > 0)
    }

    private fun track(hash: String): String {
        hashes += hash
        return hash
    }

    private fun rateKey(hash: String): String = "auth:phone-verification:rate:$hash"

    private fun dailyKey(hash: String): String {
        return redisTemplate.keys("auth:phone-verification:daily:$hash:*").single()
    }
}
