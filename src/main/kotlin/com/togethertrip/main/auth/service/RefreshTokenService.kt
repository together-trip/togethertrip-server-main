package com.togethertrip.main.auth.service

import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import com.togethertrip.main.global.security.jwt.TokenType
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

        return savedRefreshToken == refreshToken
    }

    fun delete(userId: Long) {
        redisTemplate.delete(getKey(userId))
    }

    private fun getKey(userId: Long): String {
        return "auth:refresh:$userId"
    }
}