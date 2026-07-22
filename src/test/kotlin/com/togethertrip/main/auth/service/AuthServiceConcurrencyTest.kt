package com.togethertrip.main.auth.service

import com.togethertrip.main.auth.dto.TokenResponse
import com.togethertrip.main.auth.dto.request.TokenRefreshRequest
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import com.togethertrip.main.user.domain.User
import jakarta.persistence.EntityManager
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@MainIntegrationTest
class AuthServiceConcurrencyTest @Autowired constructor(
    private val authService: AuthService,
    private val refreshTokenService: RefreshTokenService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val redisTemplate: StringRedisTemplate,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
) {

    private val userIds = mutableSetOf<Long>()

    @AfterEach
    fun cleanRedisKeys() {
        redisTemplate.delete(userIds.map { "auth:refresh:$it" })
    }

    @Test
    fun `동일 refresh token 재발급 16건이 동시에 시작해도 하나만 성공한다`() {
        val user = createUser("동시 재발급 사용자")
        val currentToken = jwtTokenProvider.createRefreshToken(user.id, user.role)
        refreshTokenService.save(user.id, currentToken)
        waitUntilNextJwtSecond()
        val contenderCount = 16
        val executor = Executors.newFixedThreadPool(contenderCount)
        val ready = CountDownLatch(contenderCount)
        val start = CountDownLatch(1)

        try {
            val futures = (0 until contenderCount).map {
                executor.submit<Any> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    try {
                        authService.refreshToken(TokenRefreshRequest(currentToken))
                    } catch (exception: BusinessException) {
                        exception.errorCode
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val outcomes = futures.map { it.get(10, TimeUnit.SECONDS) }
            val success = outcomes.filterIsInstance<TokenResponse>()
            assertEquals(1, success.size)
            assertEquals(15, outcomes.count { it == AuthErrorCode.INVALID_REFRESH_TOKEN })
            assertNotEquals(currentToken, success.single().refreshToken)
            assertTrue(refreshTokenService.matches(user.id, success.single().refreshToken))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `refresh와 logout이 동시에 시작하면 20회 모두 최종 token이 삭제된다`() {
        val user = createUser("동시 로그아웃 사용자")
        val currentToken = jwtTokenProvider.createRefreshToken(user.id, user.role)
        waitUntilNextJwtSecond()
        val executor = Executors.newFixedThreadPool(2)

        try {
            repeat(20) { round ->
                refreshTokenService.save(user.id, currentToken)
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val refreshFuture = executor.submit<Any> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    try {
                        authService.refreshToken(TokenRefreshRequest(currentToken))
                    } catch (exception: BusinessException) {
                        exception.errorCode
                    }
                }
                val logoutFuture = executor.submit<Unit> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    authService.logout(user.id)
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS))
                start.countDown()

                val refreshOutcome = refreshFuture.get(10, TimeUnit.SECONDS)
                logoutFuture.get(10, TimeUnit.SECONDS)
                assertTrue(
                    refreshOutcome is TokenResponse || refreshOutcome == AuthErrorCode.INVALID_REFRESH_TOKEN,
                    "round=$round outcome=$refreshOutcome",
                )
                assertFalse(redisTemplate.hasKey("auth:refresh:${user.id}"), "round=$round")
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun createUser(nickname: String): User {
        return requireNotNull(transactionTemplate.execute {
            User(nickname = nickname).also {
                entityManager.persist(it)
                entityManager.flush()
                userIds += it.id
            }
        })
    }

    private fun waitUntilNextJwtSecond() {
        TimeUnit.MILLISECONDS.sleep(1_100)
    }
}
