package com.togethertrip.main.triprecap.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.triprecap.domain.TripRecap
import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.triprecap.exception.TripRecapErrorCode
import com.togethertrip.main.triprecap.repository.TripRecapRepository
import com.togethertrip.main.triprecap.service.ai.TripRecapGenerateRequest
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripRecapGenerationContextLoaderTest {

    private lateinit var repository: TripRecapRepository
    private lateinit var collector: TripRecapDataCollector
    private lateinit var loader: TripRecapGenerationContextLoader

    @BeforeEach
    fun setUp() {
        repository = mock(TripRecapRepository::class.java)
        collector = mock(TripRecapDataCollector::class.java)
        loader = TripRecapGenerationContextLoader(repository, collector)
    }

    @Test
    fun `recap이 없으면 생성 context 조회에 실패한다`() {
        val exception = assertBusinessException { loader.load(999L) }

        assertEquals(TripRecapErrorCode.TRIP_RECAP_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(collector)
    }

    @Test
    fun `creating 상태가 아니면 데이터 수집 없이 종료한다`() {
        val (recap, _) = recap().also { (recap, _) ->
            recap.complete(3, java.time.Instant.now())
        }
        `when`(repository.findByIdAndDeletedAtIsNull(100L)).thenReturn(recap)

        assertNull(loader.load(100L))
        verifyNoInteractions(collector)
    }

    @Test
    fun `creating recap은 여행 데이터와 스타일을 포함한 context를 만든다`() {
        val (recap, request) = recap()
        `when`(repository.findByIdAndDeletedAtIsNull(100L)).thenReturn(recap)
        `when`(collector.collect(recap.trip, TripRecapStyle.ILLUSTRATION)).thenReturn(request)

        val context = loader.load(100L)

        assertEquals(100L, context?.recapId)
        assertEquals(10L, context?.tripId)
        assertEquals(TripRecapStyle.ILLUSTRATION, context?.style)
        assertEquals(request, context?.request)
    }

    private fun recap(): Pair<TripRecap, TripRecapGenerateRequest> {
        val user = User(nickname = "재완", status = UserStatus.ACTIVE).apply { id = 1L }
        val trip = Trip(
            ownerUser = user,
            title = "제주 여행",
            defaultCurrency = "KRW",
            startDate = LocalDate.parse("2026-07-01"),
            endDate = LocalDate.parse("2026-07-03"),
            settlementStatus = TripSettlementStatus.SETTLED,
        ).apply { id = 10L }
        val recap = TripRecap(trip, user, TripRecapStyle.ILLUSTRATION).apply { id = 100L }
        val request = TripRecapGenerateRequest(
            tripTitle = trip.title,
            startDate = trip.startDate,
            endDate = trip.endDate,
            defaultCurrency = trip.defaultCurrency,
            memberCount = 2,
            style = TripRecapStyle.ILLUSTRATION,
            countries = emptyList(),
            places = emptyList(),
            expenseSignals = emptyList(),
            photoReferences = emptyList(),
        )
        return recap to request
    }

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try {
            block()
            throw AssertionError("BusinessException이 발생해야 합니다.")
        } catch (exception: BusinessException) {
            exception
        }
    }
}
