package com.togethertrip.main.place.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.place.exception.PlaceErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class RedisPlaceRequestRateLimiterTest {

    @Test
    fun `분당 첫 요청은 count key에 TTL을 설정한다`() {
        val fixture = fixture(count = 1)

        fixture.rateLimiter.validate(7, PlaceOperation.AUTOCOMPLETE)

        verify(fixture.redisTemplate).expire(
            org.mockito.ArgumentMatchers.argThat<String> { value ->
                value.startsWith("place:rate:autocomplete:7:")
            },
            org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(2)),
        )
    }

    @Test
    fun `첫 요청이 아니면 TTL을 다시 설정하지 않는다`() {
        val fixture = fixture(count = 2)

        fixture.rateLimiter.validate(7, PlaceOperation.DETAIL)

        verify(fixture.redisTemplate, never()).expire(
            anyString(),
            org.mockito.ArgumentMatchers.any(Duration::class.java),
        )
    }

    @Test
    fun `작업별 분당 한도를 넘으면 429 오류를 반환한다`() {
        val fixture = fixture(count = 61)

        val exception = assertThrows(BusinessException::class.java) {
            fixture.rateLimiter.validate(7, PlaceOperation.AUTOCOMPLETE)
        }

        assertEquals(PlaceErrorCode.PLACE_RATE_LIMIT_EXCEEDED, exception.errorCode)
    }

    @Test
    fun `Redis increment 결과가 없으면 첫 요청이나 초과 요청으로 처리하지 않는다`() {
        val fixture = fixture(count = null)

        fixture.rateLimiter.validate(7, PlaceOperation.REVERSE_GEOCODE)

        verify(fixture.redisTemplate, never()).expire(
            anyString(),
            org.mockito.ArgumentMatchers.any(Duration::class.java),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun fixture(count: Long?): Fixture {
        val redisTemplate = mock(StringRedisTemplate::class.java)
        val valueOperations = mock(ValueOperations::class.java) as ValueOperations<String, String>
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        `when`(valueOperations.increment(anyString())).thenReturn(count)
        return Fixture(
            redisTemplate = redisTemplate,
            rateLimiter = RedisPlaceRequestRateLimiter(redisTemplate),
        )
    }

    private data class Fixture(
        val redisTemplate: StringRedisTemplate,
        val rateLimiter: RedisPlaceRequestRateLimiter,
    )
}
