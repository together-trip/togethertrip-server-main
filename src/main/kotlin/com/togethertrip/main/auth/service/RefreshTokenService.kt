package com.togethertrip.main.auth.service

import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import com.togethertrip.main.global.security.jwt.TokenType
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RefreshTokenService(
    private val redisTemplate: StringRedisTemplate,
    private val jwtTokenProvider: JwtTokenProvider,
) {

    fun save(
        userId: Long,
        refreshToken: String,
    ) {
        val key = getKey(userId)
        val ttl = Duration.ofSeconds(
            jwtTokenProvider.getExpirationSeconds(TokenType.REFRESH)
        )

        // Redis refresh token 저장
        redisTemplate.opsForValue().set(
            key,
            refreshToken,
            ttl,
        )
    }

    fun matches(
        userId: Long,
        refreshToken: String,
    ): Boolean {
        val key = getKey(userId)
        val savedRefreshToken = redisTemplate.opsForValue().get(key)

        // 저장된 refresh token 비교
        return savedRefreshToken == refreshToken
    }

    fun rotate(
        userId: Long,
        currentRefreshToken: String,
        newRefreshToken: String,
    ): Boolean {
        // Redis refresh token 교체
        val ttlSeconds = jwtTokenProvider.getExpirationSeconds(TokenType.REFRESH)
        val result = redisTemplate.execute(
            ROTATE_SCRIPT,
            listOf(getKey(userId)),
            currentRefreshToken,
            newRefreshToken,
            ttlSeconds.toString(),
        )

        // token 교체 성공 여부
        return result == 1L
    }

    fun delete(userId: Long) {
        redisTemplate.delete(getKey(userId))
    }

    private fun getKey(userId: Long): String {
        // 사용자 refresh token key
        return "auth:refresh:$userId"
    }

    companion object {
        private val ROTATE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
                return 1
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}
