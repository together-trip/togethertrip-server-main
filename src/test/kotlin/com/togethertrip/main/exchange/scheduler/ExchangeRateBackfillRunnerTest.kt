package com.togethertrip.main.exchange.scheduler

import com.togethertrip.main.exchange.config.ExchangeRateProperties
import com.togethertrip.main.exchange.service.ExchangeRateImportService
import com.togethertrip.main.exchange.support.ExchangeRateDistributedLock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Duration
import java.time.LocalDate

class ExchangeRateBackfillRunnerTest {

    private lateinit var properties: ExchangeRateProperties
    private lateinit var distributedLock: ExchangeRateDistributedLock
    private lateinit var importService: ExchangeRateImportService
    private lateinit var runner: ExchangeRateBackfillRunner

    @BeforeEach
    fun setUp() {
        properties = ExchangeRateProperties()
        distributedLock = mock(ExchangeRateDistributedLock::class.java)
        importService = mock(ExchangeRateImportService::class.java)
        runner = ExchangeRateBackfillRunner(
            properties = properties,
            distributedLock = distributedLock,
            importService = importService,
        )
    }

    @Test
    fun `backfill disabled이면 실행하지 않는다`() {
        runner.run()

        verify(distributedLock, never()).runIfAcquired(anyUnitBlock())
    }

    @Test
    fun `설정된 날짜 범위를 순서대로 수집한다`() {
        properties.backfill.enabled = true
        properties.backfill.from = LocalDate.parse("2026-06-08")
        properties.backfill.to = LocalDate.parse("2026-06-10")
        properties.backfill.pauseBetweenRequests = Duration.ZERO
        `when`(distributedLock.runIfAcquired(anyUnitBlock())).thenAnswer { invocation ->
            invocation.getArgument<() -> Unit>(0).invoke()
        }

        runner.run()

        verify(importService).importMissingRates(
            from = LocalDate.parse("2026-06-08"),
            to = LocalDate.parse("2026-06-10"),
            maxDays = 31,
            pauseBetweenRequests = Duration.ZERO,
        )
    }

    @Test
    fun `긴 범위도 최대 처리 일수와 함께 수집 서비스에 위임한다`() {
        properties.backfill.enabled = true
        properties.backfill.from = LocalDate.parse("2015-01-01")
        properties.backfill.to = LocalDate.parse("2026-06-11")
        properties.backfill.maxDaysPerRun = 1000
        properties.backfill.pauseBetweenRequests = Duration.ofMillis(300)
        `when`(distributedLock.runIfAcquired(anyUnitBlock())).thenAnswer { invocation ->
            invocation.getArgument<() -> Unit>(0).invoke()
        }

        runner.run()

        verify(importService).importMissingRates(
            from = LocalDate.parse("2015-01-01"),
            to = LocalDate.parse("2026-06-11"),
            maxDays = 1000,
            pauseBetweenRequests = Duration.ofMillis(300),
        )
    }

    private fun anyUnitBlock(): () -> Unit {
        return any<() -> Unit>() ?: {}
    }
}
