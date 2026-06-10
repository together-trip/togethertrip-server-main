package com.togethertrip.main.exchange.service

import com.togethertrip.main.exchange.client.KoreaEximExchangeRateClient
import com.togethertrip.main.exchange.client.KoreaEximExchangeRateFetchResult
import com.togethertrip.main.exchange.client.KoreaEximExchangeRateResponse
import com.togethertrip.main.exchange.client.KoreaEximExchangeRateResultCode
import com.togethertrip.main.exchange.config.ExchangeRateProperties
import com.togethertrip.main.exchange.domain.ExchangeRateImportRow
import com.togethertrip.main.exchange.repository.ExchangeRateUpsertRepository
import com.togethertrip.main.exchange.service.normalizer.KoreaEximExchangeRateNormalizer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExchangeRateImportServiceTest {

    private lateinit var client: KoreaEximExchangeRateClient
    private lateinit var normalizer: KoreaEximExchangeRateNormalizer
    private lateinit var properties: ExchangeRateProperties
    private lateinit var importRunService: ExchangeRateImportRunService
    private lateinit var upsertRepository: ExchangeRateUpsertRepository
    private lateinit var service: ExchangeRateImportService

    @BeforeEach
    fun setUp() {
        client = mock(KoreaEximExchangeRateClient::class.java)
        normalizer = mock(KoreaEximExchangeRateNormalizer::class.java)
        properties = ExchangeRateProperties().apply {
            importValidation.minimumRowCount = 1
            importValidation.requiredCurrencies = listOf("USD")
        }
        importRunService = mock(ExchangeRateImportRunService::class.java)
        upsertRepository = mock(ExchangeRateUpsertRepository::class.java)
        service = ExchangeRateImportService(
            client = client,
            normalizer = normalizer,
            properties = properties,
            importRunService = importRunService,
            upsertRepository = upsertRepository,
        )
    }

    @Test
    fun `정상 응답을 정규화해 upsert 한다`() {
        val rateDate = LocalDate.parse("2026-06-10")
        val response = KoreaEximExchangeRateResponse(currencyUnit = "USD", dealBaseRate = "1,350.00")
        val row = ExchangeRateImportRow(
            baseCurrency = "KRW",
            targetCurrency = "USD",
            rate = BigDecimal("1350.000000"),
            rateDate = rateDate,
            source = "KOREA_EXIM",
        )

        `when`(client.fetchRates(rateDate)).thenReturn(
            KoreaEximExchangeRateFetchResult.Success(listOf(response))
        )
        `when`(normalizer.normalize(response, rateDate)).thenReturn(row)
        `when`(upsertRepository.upsertAll(listOf(row))).thenReturn(1)

        val result = service.importByDate(rateDate)

        assertEquals(
            ExchangeRateImportResult.Imported(
                rateDate = rateDate,
                rowCount = 1,
                upsertCount = 1,
            ),
            result,
        )
        verify(upsertRepository).upsertAll(listOf(row))
        verify(importRunService).start(rateDate)
        verify(importRunService).finish(result)
    }

    @Test
    fun `데이터 없음은 저장하지 않는다`() {
        val rateDate = LocalDate.parse("2026-06-10")

        `when`(client.fetchRates(rateDate)).thenReturn(KoreaEximExchangeRateFetchResult.NoData)

        val result = service.importByDate(rateDate)

        assertEquals(ExchangeRateImportResult.NoData(rateDate), result)
        verifyNoInteractions(normalizer, upsertRepository)
        verify(importRunService).start(rateDate)
        verify(importRunService).finish(result)
    }

    @Test
    fun `실패 result code는 저장하지 않는다`() {
        val rateDate = LocalDate.parse("2026-06-10")

        `when`(client.fetchRates(rateDate)).thenReturn(
            KoreaEximExchangeRateFetchResult.Failed(KoreaEximExchangeRateResultCode.INVALID_DATA_CODE)
        )

        val result = service.importByDate(rateDate)

        assertEquals(
            ExchangeRateImportResult.Failed(rateDate, KoreaEximExchangeRateResultCode.INVALID_DATA_CODE),
            result,
        )
        verifyNoInteractions(normalizer, upsertRepository)
        verify(importRunService).start(rateDate)
        verify(importRunService).finish(result)
    }

    @Test
    fun `수집 중 예외가 발생하면 실패 결과로 기록한다`() {
        val rateDate = LocalDate.parse("2026-06-10")

        `when`(client.fetchRates(rateDate)).thenThrow(RuntimeException("connection reset"))

        val result = service.importByDate(rateDate)

        assertEquals(
            ExchangeRateImportResult.Error(
                rateDate = rateDate,
                message = "connection reset",
            ),
            result,
        )
        verifyNoInteractions(normalizer, upsertRepository)
        verify(importRunService).start(rateDate)
        verify(importRunService).finish(result)
    }

    @Test
    fun `필수 통화가 누락되면 저장하지 않고 실패 결과로 기록한다`() {
        val rateDate = LocalDate.parse("2026-06-10")
        val response = KoreaEximExchangeRateResponse(currencyUnit = "JPY(100)", dealBaseRate = "930.00")
        val row = ExchangeRateImportRow(
            baseCurrency = "KRW",
            targetCurrency = "JPY",
            rate = BigDecimal("9.300000"),
            rateDate = rateDate,
            source = "KOREA_EXIM",
        )

        `when`(client.fetchRates(rateDate)).thenReturn(
            KoreaEximExchangeRateFetchResult.Success(listOf(response))
        )
        `when`(normalizer.normalize(response, rateDate)).thenReturn(row)

        val result = service.importByDate(rateDate)

        assertEquals(rateDate, result.rateDate)
        assertTrue(result is ExchangeRateImportResult.Error)
        verifyNoInteractions(upsertRepository)
        verify(importRunService).start(rateDate)
        verify(importRunService).finish(result)
    }

    @Test
    fun `최근 기간 중 성공하지 않은 날짜만 자동 수집한다`() {
        val today = LocalDate.parse("2026-06-10")
        val missingDate = LocalDate.parse("2026-06-09")

        `when`(importRunService.hasCompleted(LocalDate.parse("2026-06-08"))).thenReturn(true)
        `when`(importRunService.hasCompleted(missingDate)).thenReturn(false)
        `when`(importRunService.hasCompleted(today)).thenReturn(true)
        `when`(client.fetchRates(missingDate)).thenReturn(KoreaEximExchangeRateFetchResult.NoData)

        val results = service.importMissingRecentRates(today = today, catchUpDays = 2)

        assertEquals(listOf(ExchangeRateImportResult.NoData(missingDate)), results)
        verify(client).fetchRates(missingDate)
    }

    @Test
    fun `지정 범위 중 이미 성공한 날짜는 백필 API 호출을 건너뛴다`() {
        val successDate = LocalDate.parse("2026-06-09")
        val missingDate = LocalDate.parse("2026-06-10")

        `when`(importRunService.hasCompleted(successDate)).thenReturn(true)
        `when`(importRunService.hasCompleted(missingDate)).thenReturn(false)
        `when`(client.fetchRates(missingDate)).thenReturn(KoreaEximExchangeRateFetchResult.NoData)

        val results = service.importMissingRates(
            from = successDate,
            to = missingDate,
            maxDays = 31,
            pauseBetweenRequests = Duration.ZERO,
        )

        assertEquals(listOf(ExchangeRateImportResult.NoData(missingDate)), results)
        verify(client).fetchRates(missingDate)
        verifyNoMoreInteractions(client)
    }

    @Test
    fun `백필은 성공하지 않은 날짜 중 최대 처리 일수만 수집한다`() {
        val firstDate = LocalDate.parse("2026-06-01")
        val secondDate = LocalDate.parse("2026-06-02")
        val thirdDate = LocalDate.parse("2026-06-03")

        `when`(importRunService.hasCompleted(firstDate)).thenReturn(false)
        `when`(importRunService.hasCompleted(secondDate)).thenReturn(false)
        `when`(importRunService.hasCompleted(thirdDate)).thenReturn(false)
        `when`(client.fetchRates(firstDate)).thenReturn(KoreaEximExchangeRateFetchResult.NoData)
        `when`(client.fetchRates(secondDate)).thenReturn(KoreaEximExchangeRateFetchResult.NoData)

        val results = service.importMissingRates(
            from = firstDate,
            to = thirdDate,
            maxDays = 2,
            pauseBetweenRequests = Duration.ZERO,
        )

        assertEquals(
            listOf(
                ExchangeRateImportResult.NoData(firstDate),
                ExchangeRateImportResult.NoData(secondDate),
            ),
            results,
        )
        verify(client).fetchRates(firstDate)
        verify(client).fetchRates(secondDate)
        verifyNoMoreInteractions(client)
    }

    @Test
    fun `한 날짜 수집이 예외로 실패해도 다음 날짜 수집을 계속한다`() {
        val firstDate = LocalDate.parse("2026-06-09")
        val secondDate = LocalDate.parse("2026-06-10")

        `when`(importRunService.hasCompleted(firstDate)).thenReturn(false)
        `when`(importRunService.hasCompleted(secondDate)).thenReturn(false)
        `when`(client.fetchRates(firstDate)).thenThrow(RuntimeException("timeout"))
        `when`(client.fetchRates(secondDate)).thenReturn(KoreaEximExchangeRateFetchResult.NoData)

        val results = service.importMissingRecentRates(today = secondDate, catchUpDays = 1)

        assertEquals(
            listOf(
                ExchangeRateImportResult.Error(firstDate, "timeout"),
                ExchangeRateImportResult.NoData(secondDate),
            ),
            results,
        )
        verify(client).fetchRates(firstDate)
        verify(client).fetchRates(secondDate)
        verifyNoMoreInteractions(client)
    }

    @Test
    fun `주말은 API를 호출하지 않고 비영업일로 기록한다`() {
        val saturday = LocalDate.parse("2026-06-13")

        val result = service.importByDate(saturday)

        assertEquals(ExchangeRateImportResult.NonBusinessDay(saturday), result)
        verifyNoInteractions(client, normalizer, upsertRepository)
        verify(importRunService).finish(result)
    }
}
