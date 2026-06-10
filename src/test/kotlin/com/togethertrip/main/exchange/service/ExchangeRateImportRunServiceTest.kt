package com.togethertrip.main.exchange.service

import com.togethertrip.main.exchange.client.KoreaEximExchangeRateResultCode
import com.togethertrip.main.exchange.domain.ExchangeRateImportRun
import com.togethertrip.main.exchange.domain.ExchangeRateImportRunStatus
import com.togethertrip.main.exchange.repository.ExchangeRateImportRunRepository
import org.mockito.ArgumentCaptor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ExchangeRateImportRunServiceTest {

    private lateinit var repository: ExchangeRateImportRunRepository
    private lateinit var service: ExchangeRateImportRunService

    @BeforeEach
    fun setUp() {
        repository = mock(ExchangeRateImportRunRepository::class.java)
        service = ExchangeRateImportRunService(repository)
    }

    @Test
    fun `start는 run을 생성하고 시도 횟수를 증가시킨다`() {
        val rateDate = LocalDate.parse("2026-06-10")

        `when`(repository.findActiveForUpdate("KOREA_EXIM", rateDate)).thenReturn(null)

        service.start(rateDate)

        val captor = ArgumentCaptor.forClass(ExchangeRateImportRun::class.java)
        verify(repository).save(captor.capture())

        val savedRun = captor.value
        assertEquals("KOREA_EXIM", savedRun.provider)
        assertEquals(rateDate, savedRun.rateDate)
        assertEquals(ExchangeRateImportRunStatus.RUNNING, savedRun.status)
        assertEquals(1, savedRun.attemptCount)
        assertNotNull(savedRun.startedAt)
        assertEquals(null, savedRun.finishedAt)
    }

    @Test
    fun `finish는 성공 결과를 SUCCESS로 기록한다`() {
        val rateDate = LocalDate.parse("2026-06-10")
        val run = ExchangeRateImportRun(provider = "KOREA_EXIM", rateDate = rateDate)

        `when`(repository.findActiveForUpdate("KOREA_EXIM", rateDate)).thenReturn(run)

        service.finish(
            ExchangeRateImportResult.Imported(
                rateDate = rateDate,
                rowCount = 23,
                upsertCount = 23,
            )
        )

        assertEquals(ExchangeRateImportRunStatus.SUCCESS, run.status)
        assertEquals(23, run.rowCount)
        assertEquals(23, run.upsertCount)
        assertEquals(null, run.lastResultCode)
        assertEquals(null, run.lastErrorMessage)
        assertNotNull(run.finishedAt)
        verify(repository).save(run)
    }

    @Test
    fun `finish는 API 실패를 FAILED와 result code로 기록한다`() {
        val rateDate = LocalDate.parse("2026-06-10")
        val run = ExchangeRateImportRun(provider = "KOREA_EXIM", rateDate = rateDate)

        `when`(repository.findActiveForUpdate("KOREA_EXIM", rateDate)).thenReturn(run)

        service.finish(
            ExchangeRateImportResult.Failed(
                rateDate = rateDate,
                resultCode = KoreaEximExchangeRateResultCode.INVALID_AUTH_KEY,
            )
        )

        assertEquals(ExchangeRateImportRunStatus.FAILED, run.status)
        assertEquals("3", run.lastResultCode)
        assertEquals(KoreaEximExchangeRateResultCode.INVALID_AUTH_KEY.description, run.lastErrorMessage)
        assertNotNull(run.finishedAt)
        verify(repository).save(run)
    }
}
