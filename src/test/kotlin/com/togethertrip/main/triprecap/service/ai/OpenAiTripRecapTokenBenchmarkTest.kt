package com.togethertrip.main.triprecap.service.ai

import com.togethertrip.main.triprecap.domain.TripRecapStyle
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.image.ImagePrompt
import org.springframework.ai.openai.OpenAiImageOptions
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("live-ai")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OPENAI_LIVE_BENCHMARK_APPROVED", matches = "true")
class OpenAiTripRecapTokenBenchmarkTest {

    @Test
    fun `고정 fixture 16요청의 OpenAI 이미지 토큰 기준선을 출력한다`() {
        val observations = mutableListOf<TripRecapImageGenerationObservation>()
        val properties = OpenAiTripRecapProperties().apply {
            apiKey = requireNotNull(System.getenv("OPENAI_API_KEY"))
            baseUrl = System.getenv("OPENAI_BASE_URL") ?: "https://api.openai.com"
            model = System.getenv("OPENAI_IMAGE_MODEL") ?: "gpt-image-2"
            outputProfile = System.getenv("OPENAI_IMAGE_OUTPUT_PROFILE")
                ?.let(TripRecapImageOutputProfile::valueOf)
                ?: TripRecapImageOutputProfile.ECONOMY
            size = System.getenv("OPENAI_IMAGE_SIZE") ?: "1152x2048"
            quality = System.getenv("OPENAI_IMAGE_QUALITY") ?: "medium"
            timeout = Duration.ofMinutes(5)
            maxReferenceImages = 1
        }
        val referenceImage = referenceImage()
        val output = properties.outputSettings()
        val modelRouter = DefaultTripRecapImageModelRouter()
        val modelRoute = modelRouter.route(properties.model)
        val imageOperations = DefaultSpringAiOpenAiImageOperations(properties)
        val referenceImageOptimizer = DefaultTripRecapReferenceImageOptimizer(properties)
        val optimizedReference = assertNotNull(
            referenceImageOptimizer.optimize(
                TripRecapPhotoContent("benchmark-reference.png", "image/png", referenceImage)
            )
        )
        val generator = OpenAiTripRecapGenerator(
            properties = properties,
            modelRouter = modelRouter,
            photoContentLoader = TripRecapPhotoContentLoader {
                TripRecapPhotoContent("benchmark-reference.png", "image/png", referenceImage)
            },
            referenceImageOptimizer = referenceImageOptimizer,
            imageOperations = imageOperations,
            imageGenerationObserver = observations::add,
        )

        val generatedScenes = listOf(
            request(TripRecapStyle.PHOTO, 0, false),
            request(TripRecapStyle.ILLUSTRATION, 5, false),
            request(TripRecapStyle.PHOTO, 9, true),
        ).flatMap { generator.generate(it).scenes }.toMutableList()

        val extraPrompt = generatedScenes.first { it.imagePrompt.contains("illustration") }.imagePrompt
        val extraStartedAt = System.nanoTime()
        val extraResponse = imageOperations.edit(
            imagePrompt(extraPrompt, properties, modelRoute.model),
            listOf(optimizedReference),
        )
        val extraUsage = assertNotNull(
            extraResponse.metadata.get<TripRecapImageTokenUsage>(
                DefaultSpringAiOpenAiImageOperations.USAGE_METADATA_KEY
            )
        )
        observations += TripRecapImageGenerationObservation(
            operation = "edit",
            model = modelRoute.model,
            size = output.size,
            quality = output.quality,
            referenceImageCount = 1,
            referenceImageBytes = optimizedReference.bytes.size.toLong(),
            durationMillis = (System.nanoTime() - extraStartedAt) / 1_000_000,
            success = true,
            usage = extraUsage,
        )
        val extraBytes = Base64.getDecoder().decode(
            extraResponse.results.first().output.b64Json
        )
        generatedScenes += TripRecapGeneratedScene(1, "benchmark extra illustration edit", extraPrompt, extraBytes)

        val firstPassObservations = observations.toList()
        val verifyCacheReplay = System.getenv("OPENAI_BENCHMARK_VERIFY_CACHE_REPLAY") == "true"
        if (verifyCacheReplay) {
            val replayScenes = listOf(
                request(TripRecapStyle.PHOTO, 0, false),
                request(TripRecapStyle.ILLUSTRATION, 5, false),
                request(TripRecapStyle.PHOTO, 9, true),
            ).flatMap { generator.generate(it).scenes }.toMutableList()
            val replayExtraStartedAt = System.nanoTime()
            val replayExtraResponse = imageOperations.edit(
                imagePrompt(extraPrompt, properties, modelRoute.model),
                listOf(optimizedReference),
            )
            val replayExtraCacheHit = replayExtraResponse.metadata.get<Boolean>(
                DefaultSpringAiOpenAiImageOperations.CACHE_HIT_METADATA_KEY
            ) == true
            observations += TripRecapImageGenerationObservation(
                operation = "edit",
                model = modelRoute.model,
                size = output.size,
                quality = output.quality,
                referenceImageCount = 1,
                referenceImageBytes = optimizedReference.bytes.size.toLong(),
                cacheHit = replayExtraCacheHit,
                durationMillis = (System.nanoTime() - replayExtraStartedAt) / 1_000_000,
                success = true,
                usage = null,
            )
            replayScenes += TripRecapGeneratedScene(
                1,
                "benchmark extra illustration edit",
                extraPrompt,
                Base64.getDecoder().decode(replayExtraResponse.results.first().output.b64Json),
            )

            generatedScenes.zip(replayScenes).forEach { (first, replay) ->
                assertTrue(first.imageBytes.contentEquals(replay.imageBytes))
            }
        }

        val outputDirectory = Path.of(
            System.getenv("OPENAI_BENCHMARK_OUTPUT_DIR") ?: "build/reports/openai-image-benchmark"
        )
        Files.createDirectories(outputDirectory)
        generatedScenes.forEachIndexed { index, scene ->
            Files.write(outputDirectory.resolve("scene-${index + 1}.png"), scene.imageBytes)
        }

        assertEquals(if (verifyCacheReplay) EXPECTED_REQUEST_COUNT * 2 else EXPECTED_REQUEST_COUNT, observations.size)
        assertEquals(EXPECTED_REQUEST_COUNT, generatedScenes.size)
        assertTrue(firstPassObservations.all { it.success && !it.cacheHit })
        val usages = firstPassObservations.map { assertNotNull(it.usage) }
        val totalTokens = usages.sumOf { it.totalTokens }
        val durations = firstPassObservations.map { it.durationMillis }.sorted()
        val prompts = generatedScenes.map { it.imagePrompt.length }.sorted()
        println(
            "OPENAI_IMAGE_BENCHMARK requests=${firstPassObservations.size} totalTokens=$totalTokens " +
                "tokensPerRequest=${"%.2f".format(totalTokens.toDouble() / firstPassObservations.size)} " +
                "inputTokens=${usages.sumOf { it.inputTokens }} outputTokens=${usages.sumOf { it.outputTokens }} " +
                "textInputTokens=${usages.sumOf { it.textInputTokens }} " +
                "imageInputTokens=${usages.sumOf { it.imageInputTokens }} " +
                "cachedInputTokens=${usages.sumOf { it.cachedInputTokens }} " +
                "referenceInputBytes=${firstPassObservations.sumOf { it.referenceImageBytes }} " +
                "p50Millis=${percentile(durations, .50)} p95Millis=${percentile(durations, .95)} " +
                "promptCharsMin=${prompts.first()} promptCharsP50=${percentile(prompts, .50)} " +
                "promptCharsP95=${percentile(prompts, .95)} promptCharsMax=${prompts.last()}"
        )
        if (verifyCacheReplay) {
            val replayObservations = observations.drop(EXPECTED_REQUEST_COUNT)
            assertTrue(replayObservations.all { it.success && it.cacheHit && it.usage == null })
            println(
                "OPENAI_IMAGE_CACHE_REPLAY logicalRequests=${replayObservations.size} " +
                    "externalRequests=${replayObservations.count { !it.cacheHit }} " +
                    "cacheHits=${replayObservations.count { it.cacheHit }} " +
                    "totalTokens=${replayObservations.sumOf { it.usage?.totalTokens ?: 0 }}"
            )
        }
    }

    private fun request(style: TripRecapStyle, richness: Int, withReference: Boolean) = TripRecapGenerateRequest(
        tripTitle = "AI recap token benchmark",
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 5),
        defaultCurrency = "KRW",
        memberCount = 3,
        style = style,
        countries = listOf(TripRecapCountryInput("KR", "대한민국")),
        places = (1..richness).map { TripRecapPlaceInput("benchmark-place-$it", Instant.EPOCH) },
        expenseSignals = if (richness == 0) emptyList() else {
            listOf(TripRecapExpenseSignal("FOOD", BigDecimal.TEN, "KRW", Instant.EPOCH))
        },
        photoReferences = if (withReference) {
            listOf(TripRecapPhotoReference("/benchmark-reference.png", null))
        } else emptyList(),
    )

    private fun referenceImage(): ByteArray {
        val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color(32, 96, 160)
            fillRect(0, 0, image.width, image.height)
            dispose()
        }
        return ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, "png", output))
            output.toByteArray()
        }
    }

    private fun imagePrompt(
        prompt: String,
        properties: OpenAiTripRecapProperties,
        model: String,
    ): ImagePrompt {
        val output = properties.outputSettings()
        return ImagePrompt(
            prompt,
            OpenAiImageOptions.builder()
                .model(model)
                .n(1)
                .size(output.size)
                .quality(output.quality)
                .responseFormat("b64_json")
                .build(),
        )
    }

    private fun percentile(values: List<Long>, percentile: Double) =
        values[((values.size - 1) * percentile).toInt()]

    private fun percentile(values: List<Int>, percentile: Double) =
        values[((values.size - 1) * percentile).toInt()]

    companion object {
        private const val EXPECTED_REQUEST_COUNT = 16
    }
}
