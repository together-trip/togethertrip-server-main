package com.togethertrip.main.triprecap.service

import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.triprecap.service.ai.TripRecapGenerateRequest
import com.togethertrip.main.triprecap.service.ai.TripRecapGenerateResult
import com.togethertrip.main.triprecap.service.ai.TripRecapGenerator
import com.togethertrip.main.triprecap.service.storage.TripRecapImageStorage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mockingDetails
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
}
