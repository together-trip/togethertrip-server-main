package com.togethertrip.main.triprecap.service.ai

import org.springframework.ai.image.ImageResponse
import org.springframework.stereotype.Component
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

data class TripRecapImageCacheLookup(
    val response: ImageResponse,
    val hit: Boolean,
)

@Component
class TripRecapImageRequestCache(
    private val properties: OpenAiTripRecapProperties,
) {

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>(16, .75f, true)

    fun get(
        key: String,
        cacheable: (ImageResponse) -> Boolean = { true },
        loader: () -> ImageResponse,
    ): TripRecapImageCacheLookup {
        if (
            !properties.cacheEnabled ||
            properties.cacheMaxEntries <= 0 ||
            properties.cacheTtl.isZero ||
            properties.cacheTtl.isNegative
        ) {
            return TripRecapImageCacheLookup(loader(), false)
        }

        val now = System.nanoTime()
        var creator = false
        val entry = synchronized(lock) {
            val cached = entries[key]
            if (cached != null && !isExpired(cached, now)) {
                cached
            } else {
                if (cached != null) {
                    entries.remove(key)
                }
                Entry(now).also {
                    entries[key] = it
                    creator = true
                }
            }
        }

        if (!creator) {
            return TripRecapImageCacheLookup(await(entry.future), true)
        }

        return try {
            val response = loader()
            entry.future.complete(response)
            synchronized(lock) {
                if (!cacheable(response) && entries[key] === entry) {
                    entries.remove(key)
                }
                evictCompletedEntries()
            }
            TripRecapImageCacheLookup(response, false)
        } catch (exception: Throwable) {
            entry.future.completeExceptionally(exception)
            synchronized(lock) {
                if (entries[key] === entry) {
                    entries.remove(key)
                }
            }
            throw exception
        }
    }

    private fun isExpired(entry: Entry, now: Long): Boolean {
        return now - entry.createdAtNanos >= properties.cacheTtl.toNanos()
    }

    private fun evictCompletedEntries() {
        val iterator = entries.entries.iterator()
        while (entries.size > properties.cacheMaxEntries && iterator.hasNext()) {
            if (iterator.next().value.future.isDone) {
                iterator.remove()
            }
        }
    }

    private fun await(future: CompletableFuture<ImageResponse>): ImageResponse {
        return try {
            future.join()
        } catch (exception: CompletionException) {
            throw exception.cause ?: exception
        }
    }

    private data class Entry(
        val createdAtNanos: Long,
        val future: CompletableFuture<ImageResponse> = CompletableFuture(),
    )
}
