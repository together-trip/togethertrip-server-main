package com.togethertrip.main.auth.service.phone

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.exception.BusinessException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@MainIntegrationTest
class PhoneVerificationStoreIntegrationTest @Autowired constructor(
    private val store: PhoneVerificationStore,
    private val redisTemplate: StringRedisTemplate,
) {

    private val tokens = mutableSetOf<String>()

    @AfterEach
    fun cleanRedisKeys() {
        tokens.flatMap { token -> redisTemplate.keys("auth:phone-verification:*$token*") }
            .takeIf { it.isNotEmpty() }
            ?.let(redisTemplate::delete)
    }

    @Test
    fun `인증 상태는 JSON과 TTL로 저장되고 delete가 상태와 실패 횟수를 함께 제거한다`() {
        val token = track("store-lifecycle")
        val state = state()

        store.save(token, state, Duration.ofSeconds(60))

        assertEquals(state, store.get(token))
        assertTrue(redisTemplate.getExpire(verificationKey(token), TimeUnit.SECONDS) in 58..60)
        store.incrementAttemptFailure(token, state, maxAttemptCount = 5)
        assertTrue(redisTemplate.hasKey(attemptKey(token)))

        store.delete(token)
        assertFalse(redisTemplate.hasKey(verificationKey(token)))
        assertFalse(redisTemplate.hasKey(attemptKey(token)))
    }

    @Test
    fun `다섯 번째 인증 실패는 상태와 attempt key를 제거한다`() {
        val token = track("attempt-threshold")
        val state = state()
        store.save(token, state, Duration.ofSeconds(60))

        repeat(4) { store.incrementAttemptFailure(token, state, maxAttemptCount = 5) }
        val exception = assertFailsWith<BusinessException> {
            store.incrementAttemptFailure(token, state, maxAttemptCount = 5)
        }

        assertEquals(AuthErrorCode.PHONE_VERIFICATION_ATTEMPT_EXCEEDED, exception.errorCode)
        assertFalse(redisTemplate.hasKey(verificationKey(token)))
        assertFalse(redisTemplate.hasKey(attemptKey(token)))
    }

    @Test
    fun `인증 실패 16건이 동시에 시작하면 임계 도달 1건만 상태를 제거하고 이후 요청은 만료로 처리한다`() {
        val token = track("attempt-concurrency")
        val state = state()
        store.save(token, state, Duration.ofSeconds(60))
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
                        store.incrementAttemptFailure(token, state, maxAttemptCount = 5)
                        null
                    } catch (exception: BusinessException) {
                        exception.errorCode as AuthErrorCode
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val outcomes = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(4, outcomes.count { it == null })
            assertEquals(1, outcomes.count { it == AuthErrorCode.PHONE_VERIFICATION_ATTEMPT_EXCEEDED })
            assertEquals(11, outcomes.count { it == AuthErrorCode.PHONE_VERIFICATION_CODE_EXPIRED })
            assertFalse(redisTemplate.hasKey(verificationKey(token)))
            assertFalse(redisTemplate.hasKey(attemptKey(token)))
        } finally {
            executor.shutdownNow()
        }
    }

    private fun state(): PhoneVerificationState {
        return PhoneVerificationState(
            phoneNumberHash = "hash",
            code = "123456",
            expiresAt = Instant.now().plusSeconds(60),
        )
    }

    private fun track(token: String): String {
        tokens += token
        return token
    }

    private fun verificationKey(token: String): String = "auth:phone-verification:$token"

    private fun attemptKey(token: String): String = "auth:phone-verification:attempt:$token"
}
