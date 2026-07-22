package com.togethertrip.main.auth.service.oauth

import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.exception.BusinessException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@MainIntegrationTest
class RedisOAuthSignupLockIntegrationTest @Autowired constructor(
    private val lock: RedisOAuthSignupLock,
    private val redisTemplate: StringRedisTemplate,
    private val transactionTemplate: TransactionTemplate,
) {

    private val sessions = mutableListOf<OAuthTemporarySession>()

    @AfterEach
    fun cleanRedisKeys() {
        redisTemplate.delete(sessions.map(::key))
    }

    @Test
    fun `가입 lock 16개가 동시에 경쟁하면 하나만 block에 진입한다`() {
        val session = session("concurrent")
        val contenderCount = 16
        val executor = Executors.newFixedThreadPool(contenderCount)
        val ready = CountDownLatch(contenderCount)
        val start = CountDownLatch(1)
        val winnerEntered = CountDownLatch(1)
        val releaseWinner = CountDownLatch(1)
        val conflictsFinished = CountDownLatch(contenderCount - 1)

        try {
            val futures = (0 until contenderCount).map {
                executor.submit<String> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    try {
                        lock.withLock(session) {
                            winnerEntered.countDown()
                            releaseWinner.await(10, TimeUnit.SECONDS)
                            "success"
                        }
                    } catch (exception: BusinessException) {
                        assertEquals(AuthErrorCode.SIGNUP_CONFIRMATION_IN_PROGRESS, exception.errorCode)
                        conflictsFinished.countDown()
                        "conflict"
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(winnerEntered.await(10, TimeUnit.SECONDS))
            assertTrue(conflictsFinished.await(10, TimeUnit.SECONDS))
            assertTrue(redisTemplate.hasKey(key(session)))
            releaseWinner.countDown()

            val outcomes = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, outcomes.count { it == "success" })
            assertEquals(15, outcomes.count { it == "conflict" })
            assertFalse(redisTemplate.hasKey(key(session)))
        } finally {
            releaseWinner.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `lock 소유 token이 바뀌면 finally의 Lua release가 다른 소유자 key를 삭제하지 않는다`() {
        val session = session("owner-safety")

        lock.withLock(session) {
            redisTemplate.opsForValue().set(key(session), "replacement-owner")
        }

        assertEquals("replacement-owner", redisTemplate.opsForValue().get(key(session)))
    }

    @Test
    fun `활성 transaction에서는 commit 이후에 lock을 해제한다`() {
        val session = session("transaction")

        transactionTemplate.executeWithoutResult {
            lock.withLock(session) {
                assertTrue(redisTemplate.hasKey(key(session)))
            }
            assertTrue(redisTemplate.hasKey(key(session)))
        }

        assertFalse(redisTemplate.hasKey(key(session)))
    }

    private fun session(providerUserId: String): OAuthTemporarySession {
        return OAuthTemporarySession(
            provider = OAuthProvider.KAKAO,
            providerUserId = "integration-$providerUserId",
            nickname = "테스트 사용자",
            profileImageUrl = null,
            existingUserId = null,
        ).also(sessions::add)
    }

    private fun key(session: OAuthTemporarySession): String {
        return "auth:oauth-signup-lock:${session.provider}:${session.providerUserId}"
    }
}
