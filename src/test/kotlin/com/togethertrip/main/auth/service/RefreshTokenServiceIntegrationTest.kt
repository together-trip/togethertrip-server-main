package com.togethertrip.main.auth.service

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import com.togethertrip.main.global.security.jwt.TokenType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@MainIntegrationTest
class RefreshTokenServiceIntegrationTest @Autowired constructor(
    private val service: RefreshTokenService,
    private val redisTemplate: StringRedisTemplate,
    private val jwtTokenProvider: JwtTokenProvider,
) {

    private val userIds = mutableSetOf<Long>()

    @AfterEach
    fun cleanRedisKeys() {
        redisTemplate.delete(userIds.map(::key))
    }

    @Test
    fun `refresh token 저장은 JWT refresh 만료 시간과 같은 TTL을 사용하고 삭제할 수 있다`() {
        val userId = track(91_001L)
        val expectedTtl = jwtTokenProvider.getExpirationSeconds(TokenType.REFRESH)

        service.save(userId, "refresh-token")

        assertTrue(service.matches(userId, "refresh-token"))
        assertFalse(service.matches(userId, "different-token"))
        assertTtlNear(key(userId), expectedTtl)

        service.delete(userId)
        assertFalse(service.matches(userId, "refresh-token"))
    }

    @Test
    fun `Lua rotation은 일치하는 token만 교체하고 refresh TTL을 다시 설정한다`() {
        val userId = track(91_002L)
        val expectedTtl = jwtTokenProvider.getExpirationSeconds(TokenType.REFRESH)
        redisTemplate.opsForValue().set(key(userId), "current", Duration.ofSeconds(5))

        assertTrue(service.rotate(userId, "current", "rotated"))

        assertTrue(service.matches(userId, "rotated"))
        assertTtlNear(key(userId), expectedTtl)
    }

    @Test
    fun `잘못된 token rotation은 기존 값과 남은 TTL을 보존한다`() {
        val userId = track(91_003L)
        redisTemplate.opsForValue().set(key(userId), "current", Duration.ofSeconds(30))
        val ttlBefore = requireNotNull(redisTemplate.getExpire(key(userId), TimeUnit.SECONDS))

        assertFalse(service.rotate(userId, "wrong", "must-not-be-saved"))

        val ttlAfter = requireNotNull(redisTemplate.getExpire(key(userId), TimeUnit.SECONDS))
        assertEquals("current", redisTemplate.opsForValue().get(key(userId)))
        assertTrue(ttlAfter in (ttlBefore - 2)..ttlBefore)
    }

    @Test
    fun `동일 refresh token의 동시 rotation 16건은 하나만 성공한다`() {
        val userId = track(91_004L)
        service.save(userId, "current")
        val contenderCount = 16
        val executor = Executors.newFixedThreadPool(contenderCount)
        val ready = CountDownLatch(contenderCount)
        val start = CountDownLatch(1)
        val newTokens = (0 until contenderCount).map { "rotated-$it" }

        try {
            val futures = newTokens.map { token ->
                executor.submit<Boolean> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    service.rotate(userId, "current", token)
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            assertEquals(1, futures.count { it.get(10, TimeUnit.SECONDS) })
            val storedToken = requireNotNull(redisTemplate.opsForValue().get(key(userId)))
            assertTrue(storedToken in newTokens)
            assertTtlNear(key(userId), jwtTokenProvider.getExpirationSeconds(TokenType.REFRESH))
        } finally {
            executor.shutdownNow()
        }
    }

    private fun assertTtlNear(key: String, expectedSeconds: Long) {
        val actual = requireNotNull(redisTemplate.getExpire(key, TimeUnit.SECONDS))
        assertTrue(actual in (expectedSeconds - 2)..expectedSeconds, "expected=$expectedSeconds actual=$actual")
    }

    private fun track(userId: Long): Long {
        userIds += userId
        return userId
    }

    private fun key(userId: Long): String = "auth:refresh:$userId"
}
