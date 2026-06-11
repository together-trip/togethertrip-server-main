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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ExchangeRateSchedulerTest {

    private lateinit var properties: ExchangeRateProperties
    private lateinit var distributedLock: ExchangeRateDistributedLock
    private lateinit var importService: ExchangeRateImportService
    private lateinit var scheduler: ExchangeRateScheduler

    @BeforeEach
    fun setUp() {
        properties = ExchangeRateProperties()
        distributedLock = mock(ExchangeRateDistributedLock::class.java)
        importService = mock(ExchangeRateImportService::class.java)
        scheduler = ExchangeRateScheduler(
            properties = properties,
            distributedLock = distributedLock,
            importService = importService,
            clock = Clock.fixed(
                Instant.parse("2026-06-10T02:00:00Z"),
                ZoneId.of("Asia/Seoul"),
            ),
        )
    }

    @Test
    fun `scheduler disabled이면 실행하지 않는다`() {
        scheduler.importToday()

        verify(distributedLock, never()).runIfAcquired(anySchedulerBlock())
    }

    @Test
    fun `scheduler enabled이면 최근 누락 날짜를 lock 안에서 수집한다`() {
        properties.scheduler.enabled = true
        properties.scheduler.catchUpDays = 7
        `when`(distributedLock.runIfAcquired(anySchedulerBlock())).thenAnswer { invocation ->
            invocation.getArgument<() -> List<*>>(0).invoke()
        }

        scheduler.importToday()

        verify(importService).importMissingRecentRates(
            today = LocalDate.parse("2026-06-10"),
            catchUpDays = 7,
        )
    }

    private fun anySchedulerBlock(): () -> List<*> {
        return any<() -> List<*>>() ?: { emptyList<Any>() }
    }
}
