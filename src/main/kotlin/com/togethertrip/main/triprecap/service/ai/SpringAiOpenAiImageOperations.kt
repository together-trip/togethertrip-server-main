package com.togethertrip.main.triprecap.service.ai

import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.core.ClientOptions
import com.openai.core.MultipartField
import com.openai.models.images.ImageEditParams
import com.openai.models.images.ImageGenerateParams
import com.openai.models.images.ImagesResponse
import org.springframework.ai.image.Image
import org.springframework.ai.image.ImageGeneration
import org.springframework.ai.image.ImageModel
import org.springframework.ai.image.ImagePrompt
import org.springframework.ai.image.ImageResponse
import org.springframework.ai.image.ImageResponseMetadata
import org.springframework.ai.openai.OpenAiImageOptions
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64

interface SpringAiOpenAiImageOperations : ImageModel {
    fun edit(imagePrompt: ImagePrompt, references: List<TripRecapPhotoContent>): ImageResponse
}

@Component
@ConditionalOnProperty(prefix = "trip-recap.ai", name = ["provider"], havingValue = "openai")
class DefaultSpringAiOpenAiImageOperations(
    properties: OpenAiTripRecapProperties,
    private val requestCache: TripRecapImageRequestCache = TripRecapImageRequestCache(properties),
) : SpringAiOpenAiImageOperations {

    private val client: OpenAIClient by lazy {
        OpenAIClientImpl(
            ClientOptions.builder()
                .httpClient(SpringAiOpenAiHttpClient.builder().timeout(properties.timeout).build())
                .apiKey(properties.apiKey)
                .baseUrl("${properties.baseUrl.trimEnd('/')}/v1")
                .timeout(properties.timeout)
                .maxRetries(0)
                .build()
        )
    }

    override fun call(request: ImagePrompt): ImageResponse {
        val options = request.options as? OpenAiImageOptions
            ?: error("OpenAI image options are required")
        val prompt = request.instructions.joinToString("\n") { it.text }
        val lookup = requestCache.get(
            key = cacheKey(OPERATION_GENERATION, prompt, options, emptyList()),
            loader = {
                client.images().generate(
                    ImageGenerateParams.builder()
                        .model(requireNotNull(options.model))
                        .prompt(prompt)
                        .n(requireNotNull(options.n).toLong())
                        .size(requireNotNull(options.size))
                        .quality(ImageGenerateParams.Quality.of(requireNotNull(options.quality)))
                        .outputFormat(ImageGenerateParams.OutputFormat.PNG)
                        .moderation(ImageGenerateParams.Moderation.AUTO)
                        .build()
                ).toSpringAiResponse()
            },
            cacheable = ::hasValidPngPayload,
        )
        return lookup.response.markCacheHit(lookup.hit)
    }

    override fun edit(imagePrompt: ImagePrompt, references: List<TripRecapPhotoContent>): ImageResponse {
        val options = imagePrompt.options as? OpenAiImageOptions
            ?: error("OpenAI image options are required")
        val prompt = imagePrompt.instructions.joinToString("\n") { it.text }
        val imageField = MultipartField.builder<ImageEditParams.Image>()
            .value(ImageEditParams.Image.ofInputStreams(references.map { ByteArrayInputStream(it.bytes) }))
            .filename(references.first().filename)
            .contentType(references.first().contentType)
            .build()
        val lookup = requestCache.get(
            key = cacheKey(OPERATION_EDIT, prompt, options, references),
            loader = {
                client.images().edit(
                    ImageEditParams.builder()
                        .model(requireNotNull(options.model))
                        .prompt(prompt)
                        .image(imageField)
                        .n(requireNotNull(options.n).toLong())
                        .size(requireNotNull(options.size))
                        .quality(ImageEditParams.Quality.of(requireNotNull(options.quality)))
                        .outputFormat(ImageEditParams.OutputFormat.PNG)
                        .build()
                ).toSpringAiResponse()
            },
            cacheable = ::hasValidPngPayload,
        )
        return lookup.response.markCacheHit(lookup.hit)
    }

    private fun cacheKey(
        operation: String,
        prompt: String,
        options: OpenAiImageOptions,
        references: List<TripRecapPhotoContent>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(
            operation,
            prompt,
            requireNotNull(options.model),
            requireNotNull(options.n).toString(),
            requireNotNull(options.size),
            requireNotNull(options.quality),
        ).forEach { digest.updateLengthPrefixed(it.toByteArray()) }
        references.forEach { reference ->
            digest.updateLengthPrefixed(reference.filename.toByteArray())
            digest.updateLengthPrefixed(reference.contentType.toByteArray())
            digest.updateLengthPrefixed(reference.bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun MessageDigest.updateLengthPrefixed(value: ByteArray) {
        update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.size).array())
        update(value)
    }

    private fun hasValidPngPayload(response: ImageResponse): Boolean {
        val encoded = response.results?.firstOrNull()?.output?.b64Json?.takeIf { it.isNotBlank() } ?: return false
        return runCatching { Base64.getDecoder().decode(encoded) }
            .getOrNull()
            ?.let { bytes -> bytes.size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] } }
            ?: false
    }

    private fun ImageResponse.markCacheHit(hit: Boolean): ImageResponse {
        val copiedMetadata = ImageResponseMetadata(metadata.created).apply {
            metadata.get<TripRecapImageTokenUsage>(USAGE_METADATA_KEY)?.let { put(USAGE_METADATA_KEY, it) }
            put(CACHE_HIT_METADATA_KEY, hit)
        }
        return ImageResponse(results, copiedMetadata)
    }

    private fun ImagesResponse.toSpringAiResponse(): ImageResponse {
        val generations = data().orElse(emptyList()).map { image ->
            ImageGeneration(Image(image.url().orElse(null), image.b64Json().orElse(null)))
        }
        val metadata = ImageResponseMetadata(created()).apply {
            usage().ifPresent { put(USAGE_METADATA_KEY, tokenUsage(it)) }
        }
        return ImageResponse(generations, metadata)
    }

    private fun tokenUsage(usage: ImagesResponse.Usage): TripRecapImageTokenUsage {
        return TripRecapImageTokenUsage(
            totalTokens = usage.totalTokens(),
            inputTokens = usage.inputTokens(),
            outputTokens = usage.outputTokens(),
            textInputTokens = usage.inputTokensDetails().textTokens(),
            imageInputTokens = usage.inputTokensDetails().imageTokens(),
            cachedInputTokens = 0,
        )
    }

    companion object {
        const val USAGE_METADATA_KEY = "openai.image.usage"
        const val CACHE_HIT_METADATA_KEY = "openai.image.cache-hit"
        private const val OPERATION_GENERATION = "generation"
        private const val OPERATION_EDIT = "edit"
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
