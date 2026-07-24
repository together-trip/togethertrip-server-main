package com.togethertrip.main.auth.service.apple

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

interface AppleNonceStore {
    fun claim(nonce: String, ttl: Duration): Boolean
}

@Component
class RedisAppleNonceStore(
    private val redisTemplate: StringRedisTemplate,
) : AppleNonceStore {
    override fun claim(nonce: String, ttl: Duration): Boolean {
        return redisTemplate.opsForValue()
            .setIfAbsent("auth:apple:nonce:$nonce", "used", ttl) == true
    }
}
