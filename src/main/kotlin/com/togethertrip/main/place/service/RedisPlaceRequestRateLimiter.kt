package com.togethertrip.main.place.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.place.exception.PlaceErrorCode
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class RedisPlaceRequestRateLimiter(
    private val redisTemplate: StringRedisTemplate,
) : PlaceRequestRateLimiter {

    override fun validate(
        userId: Long,
        operation: PlaceOperation,
    ) {
        val minute = Instant.now().epochSecond / SECONDS_PER_MINUTE
        val key = "place:rate:${operation.name.lowercase()}:$userId:$minute"
        val count = redisTemplate.opsForValue().increment(key) ?: 0
        if (count == 1L) {
            redisTemplate.expire(key, RATE_LIMIT_TTL)
        }
        if (count > operation.requestsPerMinute) {
            throw BusinessException(PlaceErrorCode.PLACE_RATE_LIMIT_EXCEEDED)
        }
    }

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private val RATE_LIMIT_TTL = Duration.ofMinutes(2)
    }
}
