package com.togethertrip.main.auth.service

import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.dto.request.KakaoLoginRequest
import com.togethertrip.main.auth.dto.response.AuthResponse
import com.togethertrip.main.auth.dto.response.AuthStatus
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@MainIntegrationTest
@TestPropertySource(properties = ["auth.local-test.enabled=true"])
class AuthServiceOAuthSignupConcurrencyIntegrationTest @Autowired constructor(
    private val authService: AuthService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenService: RefreshTokenService,
    private val redisTemplate: StringRedisTemplate,
    private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `동일 Kakao 신규 로그인이 경합해도 한 계정만 만들고 lock 해제 뒤 기존 계정으로 재시도한다`() {
        val suffix = System.nanoTime().toString()
        val accessToken = "$LOCAL_TEST_TOKEN_PREFIX$suffix"
        val providerUserId = "local-test-concurrency-contract-$suffix"
        val lockKey = "auth:oauth-signup-lock:${OAuthProvider.KAKAO}:$providerUserId"
        val contenderCount = 12
        val executor = Executors.newFixedThreadPool(contenderCount)
        val ready = CountDownLatch(contenderCount)
        val start = CountDownLatch(1)
        installSignupDelayTrigger()

        try {
            val futures = (0 until contenderCount).map {
                executor.submit<Any> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    loginOutcome(accessToken)
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val firstWave = futures.map { it.get(15, TimeUnit.SECONDS) }
            val firstSuccesses = firstWave.filterIsInstance<AuthResponse>()
            val lockConflicts = firstWave.count {
                it == AuthErrorCode.SIGNUP_CONFIRMATION_IN_PROGRESS
            }

            assertTrue(firstSuccesses.isNotEmpty())
            assertTrue(lockConflicts > 0)
            assertEquals(
                contenderCount,
                firstSuccesses.size + lockConflicts,
            )
            firstSuccesses.forEach(::assertProfileRequiredToken)

            val userId = findSingleUserId(providerUserId)
            assertSingleOAuthRows(providerUserId, userId)
            firstSuccesses.forEach { response ->
                assertEquals(userId, jwtTokenProvider.getClaims(response.accessToken!!).userId)
                assertEquals(userId, jwtTokenProvider.getClaims(response.refreshToken!!).userId)
            }

            waitUntilLockReleased(lockKey)
            val retries = List(lockConflicts) {
                authService.loginWithKakao(KakaoLoginRequest(accessToken))
            }

            retries.forEach(::assertProfileRequiredToken)
            retries.forEach { response ->
                assertEquals(userId, jwtTokenProvider.getClaims(response.accessToken!!).userId)
                assertEquals(userId, jwtTokenProvider.getClaims(response.refreshToken!!).userId)
            }
            assertSingleOAuthRows(providerUserId, userId)
            assertTrue(refreshTokenService.matches(userId, retries.last().refreshToken!!))
            redisTemplate.delete("auth:refresh:$userId")
        } finally {
            executor.shutdownNow()
            redisTemplate.delete(lockKey)
            dropSignupDelayTrigger()
        }
    }

    private fun loginOutcome(accessToken: String): Any {
        return try {
            authService.loginWithKakao(KakaoLoginRequest(accessToken))
        } catch (exception: BusinessException) {
            exception.errorCode
        }
    }

    private fun assertProfileRequiredToken(response: AuthResponse) {
        assertEquals(AuthStatus.PROFILE_REQUIRED, response.status)
        assertNotNull(response.accessToken)
        assertNotNull(response.refreshToken)
        assertTrue(jwtTokenProvider.validateToken(response.accessToken))
        assertTrue(jwtTokenProvider.validateToken(response.refreshToken))
    }

    private fun findSingleUserId(providerUserId: String): Long {
        val userIds = jdbcTemplate.queryForList(
            """
                select user_id
                from oauth_accounts
                where provider = 'KAKAO'
                  and provider_user_id = ?
            """.trimIndent(),
            Long::class.java,
            providerUserId,
        )
        assertEquals(1, userIds.size)
        return userIds.single()
    }

    private fun assertSingleOAuthRows(
        providerUserId: String,
        userId: Long,
    ) {
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                """
                    select count(*)
                    from oauth_accounts
                    where provider = 'KAKAO'
                      and provider_user_id = ?
                """.trimIndent(),
                Int::class.java,
                providerUserId,
            ),
        )
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "select count(*) from users where id = ?",
                Int::class.java,
                userId,
            ),
        )
    }

    private fun waitUntilLockReleased(lockKey: String) {
        val deadline = System.nanoTime() + LOCK_RELEASE_TIMEOUT.toNanos()
        while (redisTemplate.hasKey(lockKey) && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10)
        }
        assertEquals(false, redisTemplate.hasKey(lockKey))
    }

    private fun installSignupDelayTrigger() {
        jdbcTemplate.execute(
            """
                create or replace function test_delay_oauth_signup()
                returns trigger
                language plpgsql
                as ${'$'}body${'$'}
                begin
                    if new.provider_user_id like 'local-test-concurrency-contract-%' then
                        perform pg_sleep(0.5);
                    end if;
                    return new;
                end;
                ${'$'}body${'$'}
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
                create trigger test_delay_oauth_signup_trigger
                before insert on oauth_accounts
                for each row execute function test_delay_oauth_signup()
            """.trimIndent()
        )
    }

    private fun dropSignupDelayTrigger() {
        jdbcTemplate.execute(
            "drop trigger if exists test_delay_oauth_signup_trigger on oauth_accounts"
        )
        jdbcTemplate.execute("drop function if exists test_delay_oauth_signup()")
    }

    companion object {
        private const val LOCAL_TEST_TOKEN_PREFIX = "local-test:concurrency-contract-"
        private val LOCK_RELEASE_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
