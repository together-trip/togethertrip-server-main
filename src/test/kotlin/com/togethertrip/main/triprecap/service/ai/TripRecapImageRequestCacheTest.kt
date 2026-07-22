package com.togethertrip.main.triprecap.service.ai

import org.junit.jupiter.api.Test
import org.springframework.ai.image.ImageResponse
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TripRecapImageRequestCacheTest {

    @Test
    fun `same key is loaded once and then returned as a hit`() {
        val cache = cache()
        val response = ImageResponse(emptyList())
        var loads = 0

        val first = cache.get("same") { loads += 1; response }
        val second = cache.get("same") { loads += 1; error("must not reload") }

        assertFalse(first.hit)
        assertTrue(second.hit)
        assertSame(response, second.response)
        assertEquals(1, loads)
    }

    @Test
    fun `failed and non cacheable responses are not retained`() {
        val cache = cache()
        val response = ImageResponse(emptyList())

        assertFailsWith<IllegalStateException> {
            cache.get("failed") { error("temporary failure") }
        }
        val afterFailure = cache.get("failed") { response }
        val nonCacheable = cache.get("invalid", cacheable = { false }) { response }
        val afterInvalid = cache.get("invalid") { response }

        assertFalse(afterFailure.hit)
        assertFalse(nonCacheable.hit)
        assertFalse(afterInvalid.hit)
    }

    @Test
    fun `concurrent identical requests share one in flight load`() {
        val cache = cache()
        val response = ImageResponse(emptyList())
        val loads = AtomicInteger()
        val loaderStarted = CountDownLatch(1)
        val releaseLoader = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit<TripRecapImageCacheLookup> {
                cache.get("concurrent") {
                    loads.incrementAndGet()
                    loaderStarted.countDown()
                    check(releaseLoader.await(1, TimeUnit.SECONDS))
                    response
                }
            }
            assertTrue(loaderStarted.await(1, TimeUnit.SECONDS))
            val second = executor.submit<TripRecapImageCacheLookup> {
                cache.get("concurrent") { error("second loader must not run") }
            }
            Thread.sleep(20)
            releaseLoader.countDown()

            assertFalse(first.get(1, TimeUnit.SECONDS).hit)
            assertTrue(second.get(1, TimeUnit.SECONDS).hit)
            assertEquals(1, loads.get())
        } finally {
            releaseLoader.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `disabled zero sized and expired cache entries reload`() {
        val response = ImageResponse(emptyList())
        val properties = OpenAiTripRecapProperties().apply { cacheEnabled = false }
        assertFalse(TripRecapImageRequestCache(properties).get("disabled") { response }.hit)

        properties.cacheEnabled = true
        properties.cacheMaxEntries = 0
        assertFalse(TripRecapImageRequestCache(properties).get("zero") { response }.hit)

        properties.cacheMaxEntries = 1
        properties.cacheTtl = Duration.ofNanos(-1)
        assertFalse(TripRecapImageRequestCache(properties).get("negative") { response }.hit)

        properties.cacheTtl = Duration.ofNanos(1)
        val expiring = TripRecapImageRequestCache(properties)
        expiring.get("expired") { response }
        assertFalse(expiring.get("expired") { response }.hit)
    }

    @Test
    fun `least recently used completed entry is evicted at the configured bound`() {
        val cache = cache()
        val response = ImageResponse(emptyList())

        cache.get("first") { response }
        cache.get("second") { response }
        assertTrue(cache.get("first") { error("first must be retained by access") }.hit)
        cache.get("third") { response }

        assertFalse(cache.get("second") { response }.hit)
        assertTrue(cache.get("third") { error("third must still be cached") }.hit)
    }

    private fun cache(): TripRecapImageRequestCache {
        val properties = OpenAiTripRecapProperties().apply {
            cacheEnabled = true
            cacheMaxEntries = 2
            cacheTtl = Duration.ofMinutes(1)
        }
        return TripRecapImageRequestCache(properties)
    }
}
