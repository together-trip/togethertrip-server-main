package com.togethertrip.main.triprecap.service.ai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.togethertrip.main.triprecap.domain.TripRecapStyle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetSocketAddress
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenAiTripRecapGeneratorTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `text only recap calls image generations API and decodes images`() {
        val generatedBytes = pngPayload(1)
        val requests = startServer(generatedBytes)
        val generator = generator(photoContentLoader = TripRecapPhotoContentLoader { null })

        val result = generator.generate(request())

        assertEquals("openai", result.provider)
        assertEquals("gpt-image-2", result.model)
        assertEquals(3, result.scenes.size)
        result.scenes.forEach { assertContentEquals(generatedBytes, it.imageBytes) }
        assertEquals(3, requests.size)
        requests.forEach { captured ->
            assertEquals("/v1/images/generations", captured.path)
            assertEquals("Bearer test-openai-key", captured.authorization)
            assertTrue(captured.contentType.startsWith("application/json"))
            assertTrue(captured.body.contains("\"size\":\"1152x2048\""))
            assertTrue(captured.body.contains("\"quality\":\"medium\""))
            assertTrue(captured.body.contains("no recognizable face", ignoreCase = true))
        }
    }

    @Test
    fun `image response larger than default webclient buffer is decoded`() {
        val generatedBytes = ByteArray(300 * 1024).apply {
            pngPayload(1).copyInto(this)
        }
        startServer(generatedBytes)
        val generator = generator(photoContentLoader = TripRecapPhotoContentLoader { null })

        val result = generator.generate(request())

        result.scenes.forEach { assertContentEquals(generatedBytes, it.imageBytes) }
    }

    @Test
    fun `photo recap sends local references to image edits API`() {
        val requests = startServer(pngPayload(9))
        val generator = generator(
            photoContentLoader = TripRecapPhotoContentLoader {
                TripRecapPhotoContent(
                    filename = "reference.png",
                    contentType = "image/png",
                    bytes = byteArrayOf(0x01, 0x02, 0x03),
                )
            }
        )

        val result = generator.generate(
            request(
                photoReferences = listOf(
                    TripRecapPhotoReference(
                        imageUrl = "/uploads/post-attachments/reference.png",
                        thumbnailUrl = null,
                    )
                )
            )
        )

        assertEquals(3, result.scenes.size)
        assertEquals(3, requests.size)
        requests.forEach { captured ->
            assertEquals("/v1/images/edits", captured.path)
            assertTrue(captured.contentType.startsWith("multipart/form-data"))
            assertTrue(captured.body.contains("name=\"image[]\""))
            assertTrue(captured.body.contains("filename=\"reference.png\""))
            assertTrue(captured.body.contains("gpt-image-2"))
        }
    }

    @Test
    fun `openai provider requires API key before making requests`() {
        val properties = properties().apply { apiKey = "" }
        val generator = OpenAiTripRecapGenerator(
            webClientBuilder = WebClient.builder(),
            properties = properties,
            photoContentLoader = TripRecapPhotoContentLoader { null },
        )

        assertFailsWith<IllegalArgumentException> {
            generator.generate(request())
        }
    }

    @Test
    fun `invalid provider configuration is rejected before making requests`() {
        val cases = listOf<(OpenAiTripRecapProperties) -> Unit>(
            { it.model = " " },
            { it.baseUrl = "::not-a-uri" },
            { it.baseUrl = "http://example.com" },
            { it.baseUrl = "ftp://localhost" },
            { it.quality = "ultra" },
            { it.size = "1152-2048" },
            { it.size = "999999999999999999999999x2048" },
            { it.size = "1152x999999999999999999999999" },
            { it.size = "1153x2048" },
            { it.size = "1024x1024" },
            { it.size = "2304x4096" },
            { it.size = "576x1024" },
        )

        cases.forEach { mutate ->
            val properties = properties().also(mutate)
            val generator = OpenAiTripRecapGenerator(
                webClientBuilder = WebClient.builder(),
                properties = properties,
                photoContentLoader = TripRecapPhotoContentLoader { error("loader must not be called") },
            )

            assertFailsWith<IllegalArgumentException> {
                generator.generate(request())
            }
        }
    }

    @Test
    fun `missing malformed and non png image payloads are rejected`() {
        val cases = listOf(
            "{}" to "did not contain image data",
            "{\"data\":[]}" to "did not contain image data",
            "{\"data\":[{}]}" to "did not contain image data",
            "{\"data\":[{\"b64_json\":\" \"}]}" to "did not contain image data",
            "{\"data\":[{\"b64_json\":\"not-base64!\"}]}" to "invalid base64 data",
            imageResponse(byteArrayOf(0x01, 0x02)) to "invalid image size",
            imageResponse(ByteArray(9) { 0x01 }) to "did not contain a PNG image",
        )

        cases.forEach { (body, expectedMessage) ->
            startServerResponse(body)
            val failure = assertFailsWith<RuntimeException> {
                generator(TripRecapPhotoContentLoader { null }).generate(request())
            }
            assertContains(failure.message.orEmpty(), expectedMessage)
            server?.stop(0)
            server = null
        }
    }

    @Test
    fun `illustration request with medium richness uses five scenes and prompt fallbacks`() {
        startServer(pngPayload(3))
        val generator = generator(TripRecapPhotoContentLoader { null })
        val expenses = listOf("FOOD", null, "FOOD", "TRANSPORT", null).mapIndexed { index, category ->
            TripRecapExpenseSignal(
                category = category,
                amount = BigDecimal.TEN,
                currency = "KRW",
                occurredAt = Instant.EPOCH.plusSeconds(index.toLong()),
            )
        }

        val result = generator.generate(
            request().copy(
                style = TripRecapStyle.ILLUSTRATION,
                countries = emptyList(),
                expenseSignals = expenses,
            )
        )

        assertEquals(5, result.scenes.size)
        assertEquals("editorial travel illustration scene focused on FOOD", result.scenes[0].sceneDescription)
        assertEquals("editorial travel illustration scene focused on 제주 여행", result.scenes[1].sceneDescription)
        result.scenes.forEach { scene ->
            assertContains(scene.imagePrompt, "warm editorial travel illustration")
            assertContains(scene.imagePrompt, "countries unspecified destination")
            assertContains(scene.imagePrompt, "places no named place")
            assertContains(scene.imagePrompt, "food, transport as optional activity signals")
        }
    }

    @Test
    fun `high richness request is capped at seven scenes and prioritizes place focus`() {
        startServer(pngPayload(4))
        val places = (1..10).map { TripRecapPlaceInput("place-$it", Instant.EPOCH) }

        val result = generator(TripRecapPhotoContentLoader { null }).generate(
            request().copy(places = places)
        )

        assertEquals(7, result.scenes.size)
        result.scenes.forEachIndexed { index, scene ->
            assertEquals("cinematic travel photo scene focused on place-${index + 1}", scene.sceneDescription)
            assertContains(scene.imagePrompt, "Create scene ${index + 1} of 7")
        }
    }

    @Test
    fun `reference selection is capped and rotates deterministically between scenes`() {
        startServer(pngPayload(5))
        val loadedUrls = mutableListOf<String>()
        val references = (0..4).map {
            TripRecapPhotoReference(imageUrl = "/photo-$it.png", thumbnailUrl = null)
        }
        val properties = properties().apply { maxReferenceImages = 2 }
        val generator = OpenAiTripRecapGenerator(
            webClientBuilder = WebClient.builder(),
            properties = properties,
            photoContentLoader = TripRecapPhotoContentLoader { reference ->
                loadedUrls += reference.imageUrl
                TripRecapPhotoContent(
                    filename = reference.imageUrl.removePrefix("/"),
                    contentType = "image/png",
                    bytes = pngPayload(6),
                )
            },
        )

        val result = generator.generate(request(photoReferences = references))

        assertEquals(5, result.scenes.size)
        assertEquals(
            listOf(
                "/photo-0.png", "/photo-1.png",
                "/photo-2.png", "/photo-3.png",
                "/photo-4.png", "/photo-0.png",
                "/photo-1.png", "/photo-2.png",
                "/photo-3.png", "/photo-4.png",
            ),
            loadedUrls,
        )
    }

    @Test
    fun `disabled references skip loader and use generations API`() {
        val requests = startServer(pngPayload(7))
        var loaderCalled = false
        val properties = properties().apply { maxReferenceImages = -1 }
        val generator = OpenAiTripRecapGenerator(
            webClientBuilder = WebClient.builder(),
            properties = properties,
            photoContentLoader = TripRecapPhotoContentLoader {
                loaderCalled = true
                null
            },
        )

        generator.generate(
            request(
                photoReferences = listOf(TripRecapPhotoReference("/reference.png", null))
            )
        )

        assertFalse(loaderCalled)
        assertTrue(requests.all { it.path == "/v1/images/generations" })
    }

    private fun generator(photoContentLoader: TripRecapPhotoContentLoader): OpenAiTripRecapGenerator {
        return OpenAiTripRecapGenerator(
            webClientBuilder = WebClient.builder(),
            properties = properties(),
            photoContentLoader = photoContentLoader,
        )
    }

    private fun properties(): OpenAiTripRecapProperties {
        return OpenAiTripRecapProperties().apply {
            baseUrl = "http://127.0.0.1:${server?.address?.port ?: 1}"
            apiKey = "test-openai-key"
            model = "gpt-image-2"
            size = "1152x2048"
            quality = "medium"
        }
    }

    private fun startServer(imageBytes: ByteArray): List<CapturedRequest> {
        return startServerResponse(imageResponse(imageBytes))
    }

    private fun startServerResponse(responseBody: String): List<CapturedRequest> {
        val requests = CopyOnWriteArrayList<CapturedRequest>()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests += capture(exchange)
                val response = responseBody.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }
        return requests
    }

    private fun imageResponse(imageBytes: ByteArray): String {
        val encodedImage = Base64.getEncoder().encodeToString(imageBytes)
        return """{"data":[{"b64_json":"$encodedImage"}]}"""
    }

    private fun capture(exchange: HttpExchange): CapturedRequest {
        return CapturedRequest(
            path = exchange.requestURI.path,
            authorization = exchange.requestHeaders.getFirst("Authorization"),
            contentType = exchange.requestHeaders.getFirst("Content-Type").orEmpty(),
            body = exchange.requestBody.use { it.readBytes() }.toString(Charsets.ISO_8859_1),
        )
    }

    private fun request(
        photoReferences: List<TripRecapPhotoReference> = emptyList(),
    ): TripRecapGenerateRequest {
        return TripRecapGenerateRequest(
            tripTitle = "제주 여행",
            startDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 6, 5),
            defaultCurrency = "KRW",
            memberCount = 3,
            style = TripRecapStyle.PHOTO,
            countries = listOf(TripRecapCountryInput("KR", "대한민국")),
            places = emptyList(),
            expenseSignals = emptyList(),
            photoReferences = photoReferences,
        )
    }

    private fun pngPayload(suffix: Int): ByteArray {
        return byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            suffix.toByte(),
        )
    }

    private data class CapturedRequest(
        val path: String,
        val authorization: String?,
        val contentType: String,
        val body: String,
    )
}
