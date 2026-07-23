package com.togethertrip.main.triprecap.service

import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.triprecap.service.ai.TripRecapGenerateRequest
import com.togethertrip.main.triprecap.service.ai.TripRecapGenerateResult
import com.togethertrip.main.triprecap.service.ai.TripRecapGeneratedScene
import com.togethertrip.main.triprecap.service.ai.TripRecapGenerator
import com.togethertrip.main.triprecap.service.storage.TripRecapImageStorage
import com.togethertrip.main.triprecap.service.storage.TripRecapStoredImage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mockingDetails
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TripRecapGenerationServiceTest {

    @Test
    fun `AI 결과 장면 수가 정책 범위를 벗어나면 실패 처리한다`() {
        val contextLoader = mock(TripRecapGenerationContextLoader::class.java)
        val generator = mock(TripRecapGenerator::class.java)
        val storage = mock(TripRecapImageStorage::class.java)
        val completionService = mock(TripRecapGenerationCompletionService::class.java)
        val service = TripRecapGenerationService(
            tripRecapGenerationContextLoader = contextLoader,
            tripRecapGenerator = generator,
            tripRecapImageStorage = storage,
            tripRecapGenerationCompletionService = completionService,
        )
        val request = TripRecapGenerateRequest(
            tripTitle = "제주 여행",
            startDate = null,
            endDate = null,
            defaultCurrency = "KRW",
            memberCount = 2,
            style = TripRecapStyle.PHOTO,
            countries = emptyList(),
            places = emptyList(),
            expenseSignals = emptyList(),
            photoReferences = emptyList(),
        )
        `when`(contextLoader.load(100L)).thenReturn(
            TripRecapGenerationContext(
                recapId = 100L,
                tripId = 10L,
                style = TripRecapStyle.PHOTO,
                request = request,
            )
        )
        `when`(generator.generate(request)).thenReturn(
            TripRecapGenerateResult(
                provider = "stub",
                model = "stub",
                scenes = emptyList(),
            )
        )

        service.generate(100L)

        verifyNoInteractions(storage)
        val invocations = mockingDetails(completionService).invocations.toList()
        assertEquals(1, invocations.size)
        assertEquals("fail", invocations.first().method.name)
        assertEquals(100L, invocations.first().arguments[0])
        assertIs<IllegalArgumentException>(invocations.first().arguments[1])
    }

    @Test
    fun `이미 처리된 recap은 생성기와 스토리지를 호출하지 않는다`() {
        val fixture = fixture()
        `when`(fixture.contextLoader.load(100L)).thenReturn(null)

        fixture.service.generate(100L)

        verifyNoInteractions(fixture.generator, fixture.storage, fixture.completionService)
    }

    @Test
    fun `AI 장면을 순서대로 저장하고 provider metadata와 함께 완료한다`() {
        val fixture = fixture()
        val request = request()
        val scenes = listOf(scene(3), scene(1), scene(2))
        `when`(fixture.contextLoader.load(100L)).thenReturn(context(request))
        `when`(fixture.generator.generate(request)).thenReturn(
            TripRecapGenerateResult("openai", "gpt-image-2", scenes)
        )
        scenes.forEach { scene ->
            `when`(fixture.storage.store(10L, 100L, scene.order, scene.imageBytes)).thenReturn(
                TripRecapStoredImage(
                    objectKey = "trip-recaps/10/100/${scene.order}.png",
                    imageUrl = "/images/${scene.order}.png",
                )
            )
        }

        fixture.service.generate(100L)

        val stores = mockingDetails(fixture.storage).invocations
            .filter { it.method.name == "store" }
        assertEquals(listOf(1, 2, 3), stores.map { it.arguments[2] })
        val complete = mockingDetails(fixture.completionService).invocations
            .single { it.method.name == "complete" }
        @Suppress("UNCHECKED_CAST")
        val storedScenes = complete.arguments[1] as List<TripRecapStoredScene>
        assertEquals(listOf(1, 2, 3), storedScenes.map { it.order })
        assertEquals(listOf("openai"), storedScenes.map { it.generationProvider }.distinct())
        assertEquals(listOf("gpt-image-2"), storedScenes.map { it.generationModel }.distinct())
        assertTrue(
            mockingDetails(fixture.completionService).invocations.none { it.method.name == "fail" }
        )
    }

    @Test
    fun `장면 수가 최대치를 넘으면 저장 없이 실패 처리한다`() {
        val fixture = fixture()
        val request = request()
        `when`(fixture.contextLoader.load(100L)).thenReturn(context(request))
        `when`(fixture.generator.generate(request)).thenReturn(
            TripRecapGenerateResult("stub", "stub-v1", (1..8).map(::scene))
        )

        fixture.service.generate(100L)

        verifyNoInteractions(fixture.storage)
        val failure = mockingDetails(fixture.completionService).invocations.single()
        assertEquals("fail", failure.method.name)
        assertIs<IllegalArgumentException>(failure.arguments[1])
    }

    @Test
    fun `이미지 저장 실패는 완료하지 않고 동일 예외로 실패 처리한다`() {
        val fixture = fixture()
        val request = request()
        val failure = IllegalStateException("disk full")
        val scenes = (1..3).map(::scene)
        `when`(fixture.contextLoader.load(100L)).thenReturn(context(request))
        `when`(fixture.generator.generate(request)).thenReturn(
            TripRecapGenerateResult("stub", "stub-v1", scenes)
        )
        `when`(fixture.storage.store(10L, 100L, 1, scenes.first().imageBytes)).thenThrow(failure)

        fixture.service.generate(100L)

        val invocations = mockingDetails(fixture.completionService).invocations.toList()
        assertEquals(1, invocations.size)
        assertEquals("fail", invocations.single().method.name)
        assertEquals(failure, invocations.single().arguments[1])
    }

    private fun fixture(): Fixture {
        val contextLoader = mock(TripRecapGenerationContextLoader::class.java)
        val generator = mock(TripRecapGenerator::class.java)
        val storage = mock(TripRecapImageStorage::class.java)
        val completionService = mock(TripRecapGenerationCompletionService::class.java)
        return Fixture(
            contextLoader,
            generator,
            storage,
            completionService,
            TripRecapGenerationService(contextLoader, generator, storage, completionService),
        )
    }

    private fun request(): TripRecapGenerateRequest {
        return TripRecapGenerateRequest(
            tripTitle = "제주 여행",
            startDate = null,
            endDate = null,
            defaultCurrency = "KRW",
            memberCount = 2,
            style = TripRecapStyle.PHOTO,
            countries = emptyList(),
            places = emptyList(),
            expenseSignals = emptyList(),
            photoReferences = emptyList(),
        )
    }

    private fun context(request: TripRecapGenerateRequest): TripRecapGenerationContext {
        return TripRecapGenerationContext(100L, 10L, TripRecapStyle.PHOTO, request)
    }

    private fun scene(order: Int): TripRecapGeneratedScene {
        return TripRecapGeneratedScene(
            order = order,
            sceneDescription = "scene-$order",
            imagePrompt = "prompt-$order",
            imageBytes = byteArrayOf(order.toByte()),
        )
    }

    private data class Fixture(
        val contextLoader: TripRecapGenerationContextLoader,
        val generator: TripRecapGenerator,
        val storage: TripRecapImageStorage,
        val completionService: TripRecapGenerationCompletionService,
        val service: TripRecapGenerationService,
    )
}
