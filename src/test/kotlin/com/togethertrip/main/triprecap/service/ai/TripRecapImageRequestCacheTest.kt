package com.togethertrip.main.triprecap.service.ai

import com.togethertrip.main.global.config.MainIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.ai.image.Image
import org.springframework.ai.image.ImageGeneration
import org.springframework.ai.image.ImageResponse
import org.springframework.ai.image.ImageResponseMetadata
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@MainIntegrationTest
class TripRecapImageRequestCacheTest @Autowired constructor(
    private val redissonClient: RedissonClient,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: StringRedisTemplate,
) {

    private val keys = mutableSetOf<String>()

    @AfterEach
    fun cleanRedisKeys() {
        keys.flatMap { key ->
            listOf(
                RedisTripRecapImageRequestCache.RESPONSE_KEY_PREFIX + key,
                RedisTripRecapImageRequestCache.LOCK_KEY_PREFIX + key,
            )
        }.takeIf { it.isNotEmpty() }
            ?.let(redisTemplate::delete)
    }

    @Test
    fun `서로 다른 캐시 인스턴스의 동일 요청은 한 번만 생성한다`() {
        val key = key("shared")
        val firstCache = cache()
        val secondCache = cache()
        val response = response()
        val loads = AtomicInteger()
        val loaderStarted = CountDownLatch(1)
        val releaseLoader = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit<TripRecapImageCacheLookup> {
                firstCache.get(key) {
                    loads.incrementAndGet()
                    loaderStarted.countDown()
                    check(releaseLoader.await(3, TimeUnit.SECONDS))
                    response
                }
            }
            assertTrue(loaderStarted.await(3, TimeUnit.SECONDS))
            val second = executor.submit<TripRecapImageCacheLookup> {
                secondCache.get(key) {
                    loads.incrementAndGet()
                    error("두 번째 인스턴스는 이미 진행 중인 생성 결과를 사용해야 합니다.")
                }
            }
            releaseLoader.countDown()

            assertFalse(first.get(3, TimeUnit.SECONDS).hit)
            val shared = second.get(3, TimeUnit.SECONDS)
            assertTrue(shared.hit)
            assertEquals(
                response.results.map { it.output.url to it.output.b64Json },
                shared.response.results.map { it.output.url to it.output.b64Json },
            )
            assertEquals(
                response.metadata.get<TripRecapImageTokenUsage>(DefaultSpringAiOpenAiImageOperations.USAGE_METADATA_KEY),
                shared.response.metadata.get(DefaultSpringAiOpenAiImageOperations.USAGE_METADATA_KEY),
            )
            assertEquals(1, loads.get())
        } finally {
            releaseLoader.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `분산 lock 대기 시간이 끝나면 중복 생성을 실행하지 않는다`() {
        val key = key("timeout")
        val ownerCache = cache(lockWait = Duration.ofSeconds(2))
        val contenderCache = cache(lockWait = Duration.ofMillis(50))
        val loaderStarted = CountDownLatch(1)
        val releaseLoader = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val owner = executor.submit<TripRecapImageCacheLookup> {
                ownerCache.get(key) {
                    loaderStarted.countDown()
                    check(releaseLoader.await(3, TimeUnit.SECONDS))
                    response()
                }
            }
            assertTrue(loaderStarted.await(3, TimeUnit.SECONDS))

            assertFailsWith<IllegalStateException> {
                contenderCache.get(key) { error("lock 획득 실패 시 중복 생성하면 안 됩니다.") }
            }

            releaseLoader.countDown()
            assertFalse(owner.get(3, TimeUnit.SECONDS).hit)
        } finally {
            releaseLoader.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `실패 응답과 캐시 불가 응답은 저장하지 않는다`() {
        val failedKey = key("failed")
        val invalidKey = key("invalid")
        val cache = cache()

        assertFailsWith<IllegalStateException> {
            cache.get(failedKey) { error("temporary failure") }
        }
        assertFalse(cache.get(failedKey) { response() }.hit)
        assertFalse(cache.get(invalidKey, cacheable = { false }) { response() }.hit)
        assertFalse(cache.get(invalidKey) { response() }.hit)
    }

    @Test
    fun `TTL이 지난 응답은 다시 생성한다`() {
        val key = key("expired")
        val cache = cache(ttl = Duration.ofMillis(80))

        assertFalse(cache.get(key) { response() }.hit)
        Thread.sleep(160)

        assertFalse(cache.get(key) { response() }.hit)
    }

    @Test
    fun `캐시를 끄면 Redis를 거치지 않고 매번 생성한다`() {
        val key = key("disabled")
        val cache = cache(enabled = false)
        val loads = AtomicInteger()

        repeat(2) {
            assertFalse(cache.get(key) { loads.incrementAndGet(); response() }.hit)
        }

        assertEquals(2, loads.get())
        assertFalse(redisTemplate.hasKey(RedisTripRecapImageRequestCache.RESPONSE_KEY_PREFIX + key))
    }

    private fun cache(
        enabled: Boolean = true,
        ttl: Duration = Duration.ofMinutes(1),
        lockWait: Duration = Duration.ofSeconds(2),
    ): RedisTripRecapImageRequestCache {
        val properties = OpenAiTripRecapProperties().apply {
            cacheEnabled = enabled
            cacheTtl = ttl
            cacheLockWait = lockWait
        }
        return RedisTripRecapImageRequestCache(properties, redissonClient, objectMapper)
    }

    private fun key(label: String): String {
        return "$label-${UUID.randomUUID()}".also(keys::add)
    }

    private fun response(): ImageResponse {
        val usage = TripRecapImageTokenUsage(120, 40, 80, 10, 30, 0)
        val metadata = ImageResponseMetadata(123L).apply {
            put(DefaultSpringAiOpenAiImageOperations.USAGE_METADATA_KEY, usage)
        }
        return ImageResponse(
            listOf(ImageGeneration(Image(null, "iVBORw0KGgo="))),
            metadata,
        )
    }
}
