package com.togethertrip.main.place.service

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.place.exception.PlaceErrorCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@MainIntegrationTest
class RedisPlaceRequestRateLimiterIntegrationTest @Autowired constructor(
    private val rateLimiter: RedisPlaceRequestRateLimiter,
    private val redisTemplate: StringRedisTemplate,
) {

    private val userIds = mutableSetOf<Long>()

    @AfterEach
    fun cleanRedisKeys() {
        userIds.flatMap { userId -> redisTemplate.keys("place:rate:*:$userId:*") }
            .takeIf { it.isNotEmpty() }
            ?.let(redisTemplate::delete)
    }

    @Test
    fun `operation 종류별 rate key와 count는 서로 격리된다`() {
        val userId = track(92_001L)

        rateLimiter.validate(userId, PlaceOperation.AUTOCOMPLETE)
        rateLimiter.validate(userId, PlaceOperation.DETAIL)

        val autocompleteKey = singleKey("place:rate:autocomplete:$userId:*")
        val detailKey = singleKey("place:rate:detail:$userId:*")
        assertEquals("1", redisTemplate.opsForValue().get(autocompleteKey))
        assertEquals("1", redisTemplate.opsForValue().get(detailKey))
        assertTrue(redisTemplate.getExpire(autocompleteKey, TimeUnit.SECONDS) in 118..120)
        assertTrue(redisTemplate.getExpire(detailKey, TimeUnit.SECONDS) in 118..120)
    }

    @Test
    fun `autocomplete 요청 64건이 동시에 시작하면 한도 60건만 허용한다`() {
        val userId = track(92_002L)
        val contenderCount = 64
        val executor = Executors.newFixedThreadPool(contenderCount)
        val ready = CountDownLatch(contenderCount)
        val start = CountDownLatch(1)

        try {
            val futures = (0 until contenderCount).map {
                executor.submit<PlaceErrorCode?> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    try {
                        rateLimiter.validate(userId, PlaceOperation.AUTOCOMPLETE)
                        null
                    } catch (exception: BusinessException) {
                        exception.errorCode as PlaceErrorCode
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val outcomes = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(60, outcomes.count { it == null })
            assertEquals(4, outcomes.count { it == PlaceErrorCode.PLACE_RATE_LIMIT_EXCEEDED })
            val key = singleKey("place:rate:autocomplete:$userId:*")
            assertEquals("64", redisTemplate.opsForValue().get(key))
            assertTrue(redisTemplate.getExpire(key, TimeUnit.SECONDS) > 0)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun singleKey(pattern: String): String = redisTemplate.keys(pattern).single()

    private fun track(userId: Long): Long {
        userIds += userId
        return userId
    }
}
