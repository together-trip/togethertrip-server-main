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

interface SpringAiOpenAiImageOperations : ImageModel {
    fun edit(imagePrompt: ImagePrompt, references: List<TripRecapPhotoContent>): ImageResponse
}

@Component
@ConditionalOnProperty(prefix = "trip-recap.ai", name = ["provider"], havingValue = "openai")
class DefaultSpringAiOpenAiImageOperations(
    properties: OpenAiTripRecapProperties,
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
        val response = client.images().generate(
            ImageGenerateParams.builder()
                .model(requireNotNull(options.model))
                .prompt(prompt)
                .n(requireNotNull(options.n).toLong())
                .size(requireNotNull(options.size))
                .quality(ImageGenerateParams.Quality.of(requireNotNull(options.quality)))
                .outputFormat(ImageGenerateParams.OutputFormat.PNG)
                .moderation(ImageGenerateParams.Moderation.AUTO)
                .build()
        )
        return response.toSpringAiResponse()
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
        val response = client.images().edit(
            ImageEditParams.builder()
                .model(requireNotNull(options.model))
                .prompt(prompt)
                .image(imageField)
                .n(requireNotNull(options.n).toLong())
                .size(requireNotNull(options.size))
                .quality(ImageEditParams.Quality.of(requireNotNull(options.quality)))
                .outputFormat(ImageEditParams.OutputFormat.PNG)
                .build()
        )
        return response.toSpringAiResponse()
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
    }
}
