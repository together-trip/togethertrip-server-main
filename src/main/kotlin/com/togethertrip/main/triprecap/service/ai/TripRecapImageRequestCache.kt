package com.togethertrip.main.triprecap.service.ai

import org.redisson.api.RBucket
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.slf4j.LoggerFactory
import org.springframework.ai.image.Image
import org.springframework.ai.image.ImageGeneration
import org.springframework.ai.image.ImageResponse
import org.springframework.ai.image.ImageResponseMetadata
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

data class TripRecapImageCacheLookup(
    val response: ImageResponse,
    val hit: Boolean,
)

fun interface TripRecapImageRequestCache {
    fun get(
        key: String,
        cacheable: (ImageResponse) -> Boolean,
        loader: () -> ImageResponse,
    ): TripRecapImageCacheLookup

    fun get(key: String, loader: () -> ImageResponse): TripRecapImageCacheLookup {
        return get(key, { true }, loader)
    }

    companion object {
        val NOOP = TripRecapImageRequestCache { _, _, loader ->
            TripRecapImageCacheLookup(loader(), false)
        }
    }
}

@Component
class RedisTripRecapImageRequestCache(
    private val properties: OpenAiTripRecapProperties,
    private val redissonClient: RedissonClient,
    private val objectMapper: ObjectMapper,
) : TripRecapImageRequestCache {

    override fun get(
        key: String,
        cacheable: (ImageResponse) -> Boolean,
        loader: () -> ImageResponse,
    ): TripRecapImageCacheLookup {
        if (!isEnabled()) {
            return TripRecapImageCacheLookup(loader(), false)
        }

        val bucket = responseBucket(key)
        read(bucket)?.let { return TripRecapImageCacheLookup(it, true) }

        val lock = redissonClient.getLock("$LOCK_KEY_PREFIX$key")
        val acquired = try {
            lock.tryLock(properties.cacheLockWait.toMillis(), TimeUnit.MILLISECONDS)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("이미지 생성 분산 lock 대기 중 interrupt가 발생했습니다.", exception)
        }
        check(acquired) {
            "동일 이미지 생성 요청의 분산 lock을 ${properties.cacheLockWait} 안에 획득하지 못했습니다."
        }

        return try {
            read(bucket)?.let { return TripRecapImageCacheLookup(it, true) }

            val response = loader()
            if (cacheable(response)) {
                bucket.set(serialize(response), properties.cacheTtl)
            }
            TripRecapImageCacheLookup(response, false)
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    private fun isEnabled(): Boolean {
        return properties.cacheEnabled && !properties.cacheTtl.isZero && !properties.cacheTtl.isNegative
    }

    private fun responseBucket(key: String): RBucket<String> {
        return redissonClient.getBucket("$RESPONSE_KEY_PREFIX$key", StringCodec.INSTANCE)
    }

    private fun read(bucket: RBucket<String>): ImageResponse? {
        val cached = bucket.get() ?: return null
        return runCatching { deserialize(cached) }
            .onFailure {
                logger.warn("이미지 생성 Redis 캐시 역직렬화에 실패해 항목을 제거합니다. key={}", bucket.name, it)
                bucket.delete()
            }
            .getOrNull()
    }

    private fun serialize(response: ImageResponse): String {
        return objectMapper.writeValueAsString(
            CachedImageResponse(
                created = response.metadata.created,
                images = response.results.map { CachedImage(it.output.url, it.output.b64Json) },
                usage = response.metadata.get(DefaultSpringAiOpenAiImageOperations.USAGE_METADATA_KEY),
            )
        )
    }

    private fun deserialize(value: String): ImageResponse {
        val cached = objectMapper.readValue(value, CachedImageResponse::class.java)
        val metadata = (cached.created?.let(::ImageResponseMetadata) ?: ImageResponseMetadata()).apply {
            cached.usage?.let { put(DefaultSpringAiOpenAiImageOperations.USAGE_METADATA_KEY, it) }
        }
        return ImageResponse(
            cached.images.map { ImageGeneration(Image(it.url, it.b64Json)) },
            metadata,
        )
    }

    private data class CachedImageResponse(
        val created: Long?,
        val images: List<CachedImage>,
        val usage: TripRecapImageTokenUsage?,
    )

    private data class CachedImage(
        val url: String?,
        val b64Json: String?,
    )

    companion object {
        internal const val RESPONSE_KEY_PREFIX = "trip-recap:image-request:response:"
        internal const val LOCK_KEY_PREFIX = "trip-recap:image-request:lock:"
        private val logger = LoggerFactory.getLogger(RedisTripRecapImageRequestCache::class.java)
    }
}
