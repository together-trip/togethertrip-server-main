package com.togethertrip.main.exchange.batch

import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJobStatus
import com.togethertrip.main.exchange.repository.ExchangeRateBackfillJobRepository
import com.togethertrip.main.exchange.service.ExchangeRateImportResult
import com.togethertrip.main.exchange.service.ExchangeRateImportService
import com.togethertrip.main.exchange.support.ExchangeRateDistributedLock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJob
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class ExchangeRateBackfillBatchServiceTest {

    private lateinit var importService: ExchangeRateImportService
    private lateinit var distributedLock: ExchangeRateDistributedLock
    private lateinit var backfillJobRepository: ExchangeRateBackfillJobRepository
    private lateinit var service: ExchangeRateBackfillBatchService

    @BeforeEach
    fun setUp() {
        importService = mock(ExchangeRateImportService::class.java)
        distributedLock = mock(ExchangeRateDistributedLock::class.java)
        backfillJobRepository = mock(ExchangeRateBackfillJobRepository::class.java)
        service = ExchangeRateBackfillBatchService(
            importService = importService,
            distributedLock = distributedLock,
            backfillJobRepository = backfillJobRepository,
        )
    }

    @Test
    fun `실제 처리 대상 날짜 수를 진행률 분모로 기록한다`() {
        val backfillJobId = 10L
        val from = LocalDate.parse("2026-06-01")
        val to = LocalDate.parse("2026-06-03")
        val firstDate = LocalDate.parse("2026-06-02")
        val secondDate = LocalDate.parse("2026-06-03")

        `when`(distributedLock.runIfAcquired(anyBlock())).thenAnswer { invocation ->
            invocation.getArgument<() -> Unit>(0).invoke()
        }
        `when`(importService.findMissingRateDates(from, to)).thenReturn(listOf(firstDate, secondDate))
        `when`(importService.importByDate(firstDate)).thenReturn(ExchangeRateImportResult.NoData(firstDate))
        `when`(importService.importByDate(secondDate)).thenReturn(ExchangeRateImportResult.NonBusinessDay(secondDate))
        `when`(
            backfillJobRepository.markRunning(
                eqValue(backfillJobId),
                eqValue(ExchangeRateBackfillJobStatus.RUNNING),
                eqValue(2L),
                anyInstant(),
                anyInstant(),
            )
        ).thenReturn(1)
        `when`(
            backfillJobRepository.incrementProgress(
                eqValue(backfillJobId),
                anyIntValue(),
                anyIntValue(),
                anyIntValue(),
                anyIntValue(),
                anyInstant(),
            )
        ).thenReturn(1)
        `when`(
            backfillJobRepository.markFinished(
                eqValue(backfillJobId),
                eqValue(ExchangeRateBackfillJobStatus.COMPLETED),
                anyInstant(),
                anyInstant(),
            )
        ).thenReturn(1)

        service.run(
            backfillJobId = backfillJobId,
            from = from,
            to = to,
            pauseBetweenRequests = Duration.ZERO,
        )

        verify(backfillJobRepository).markRunning(
            eqValue(backfillJobId),
            eqValue(ExchangeRateBackfillJobStatus.RUNNING),
            eqValue(2L),
            anyInstant(),
            anyInstant(),
        )
        verify(backfillJobRepository).incrementProgress(
            eqValue(backfillJobId),
            eqValue(0),
            eqValue(0),
            eqValue(1),
            eqValue(0),
            anyInstant(),
        )
        verify(backfillJobRepository).incrementProgress(
            eqValue(backfillJobId),
            eqValue(0),
            eqValue(0),
            eqValue(0),
            eqValue(1),
            anyInstant(),
        )
        verify(backfillJobRepository).markFinished(
            eqValue(backfillJobId),
            eqValue(ExchangeRateBackfillJobStatus.COMPLETED),
            anyInstant(),
            anyInstant(),
        )
    }

    @Test
    fun `lock 획득에 실패하면 skipped locked 상태로 기록한다`() {
        val backfillJobId = 10L
        `when`(distributedLock.runIfAcquired(anyBlock())).thenReturn(null)
        `when`(
            backfillJobRepository.markFailed(
                eqValue(backfillJobId),
                eqValue(ExchangeRateBackfillJobStatus.SKIPPED_LOCKED),
                eqValue("환율 수집 lock을 획득하지 못했습니다."),
                anyInstant(),
                anyInstant(),
            )
        ).thenReturn(1)

        service.run(
            backfillJobId = backfillJobId,
            from = LocalDate.parse("2026-06-01"),
            to = LocalDate.parse("2026-06-03"),
            pauseBetweenRequests = Duration.ZERO,
        )

        verify(importService, never()).findMissingRateDates(anyLocalDate(), anyLocalDate())
        verify(backfillJobRepository).markFailed(
            eqValue(backfillJobId),
            eqValue(ExchangeRateBackfillJobStatus.SKIPPED_LOCKED),
            eqValue("환율 수집 lock을 획득하지 못했습니다."),
            anyInstant(),
            anyInstant(),
        )
    }

    @Test
    fun `일부 날짜가 실패해도 completed로 마감하고 실패 카운트를 제공한다`() {
        val backfillJobId = 10L
        val from = LocalDate.parse("2026-06-01")
        val to = LocalDate.parse("2026-06-02")
        val firstDate = LocalDate.parse("2026-06-01")
        val secondDate = LocalDate.parse("2026-06-02")

        `when`(distributedLock.runIfAcquired(anyBlock())).thenAnswer { invocation ->
            invocation.getArgument<() -> Unit>(0).invoke()
        }
        `when`(importService.findMissingRateDates(from, to)).thenReturn(listOf(firstDate, secondDate))
        `when`(importService.importByDate(firstDate)).thenReturn(ExchangeRateImportResult.NoData(firstDate))
        `when`(importService.importByDate(secondDate)).thenReturn(
            ExchangeRateImportResult.Error(secondDate, "timeout")
        )
        `when`(
            backfillJobRepository.markRunning(
                eqValue(backfillJobId),
                eqValue(ExchangeRateBackfillJobStatus.RUNNING),
                eqValue(2L),
                anyInstant(),
                anyInstant(),
            )
        ).thenReturn(1)
        `when`(
            backfillJobRepository.incrementProgress(
                eqValue(backfillJobId),
                anyIntValue(),
                anyIntValue(),
                anyIntValue(),
                anyIntValue(),
                anyInstant(),
            )
        ).thenReturn(1)
        `when`(
            backfillJobRepository.markFinished(
                eqValue(backfillJobId),
                eqValue(ExchangeRateBackfillJobStatus.COMPLETED),
                anyInstant(),
                anyInstant(),
            )
        ).thenReturn(1)

        service.run(
            backfillJobId = backfillJobId,
            from = from,
            to = to,
            pauseBetweenRequests = Duration.ZERO,
        )

        verify(backfillJobRepository).incrementProgress(
            eqValue(backfillJobId),
            eqValue(0),
            eqValue(0),
            eqValue(1),
            eqValue(0),
            anyInstant(),
        )
        verify(backfillJobRepository).incrementProgress(
            eqValue(backfillJobId),
            eqValue(0),
            eqValue(1),
            eqValue(0),
            eqValue(0),
            anyInstant(),
        )
        verify(backfillJobRepository).markFinished(
            eqValue(backfillJobId),
            eqValue(ExchangeRateBackfillJobStatus.COMPLETED),
            anyInstant(),
            anyInstant(),
        )
    }

    @Test
    fun `Batch 실패 기록이 terminal 상태 때문에 0건이면 무시한다`() {
        val backfillJobId = 10L
        val job = backfillJob(backfillJobId, ExchangeRateBackfillJobStatus.COMPLETED)
        `when`(
            backfillJobRepository.markFailedIfNotTerminal(
                eqValue(backfillJobId),
                eqValue(ExchangeRateBackfillJobStatus.FAILED),
                eqValue("batch failed"),
                anyInstant(),
                anyInstant(),
                eqValue(listOf(ExchangeRateBackfillJobStatus.COMPLETED, ExchangeRateBackfillJobStatus.SKIPPED_LOCKED)),
            )
        ).thenReturn(0)
        `when`(backfillJobRepository.findByIdAndDeletedAtIsNull(backfillJobId)).thenReturn(job)

        service.markFailedFromBatch(backfillJobId, "batch failed")
    }

    @Test
    fun `Batch 실패 기록이 0건이고 백필 작업이 없으면 예외를 던진다`() {
        val backfillJobId = 10L
        `when`(
            backfillJobRepository.markFailedIfNotTerminal(
                eqValue(backfillJobId),
                eqValue(ExchangeRateBackfillJobStatus.FAILED),
                eqValue("batch failed"),
                anyInstant(),
                anyInstant(),
                eqValue(listOf(ExchangeRateBackfillJobStatus.COMPLETED, ExchangeRateBackfillJobStatus.SKIPPED_LOCKED)),
            )
        ).thenReturn(0)
        `when`(backfillJobRepository.findByIdAndDeletedAtIsNull(backfillJobId)).thenReturn(null)

        assertThrows<IllegalArgumentException> {
            service.markFailedFromBatch(backfillJobId, "batch failed")
        }
    }

    private fun anyBlock(): () -> Unit {
        return any<() -> Unit>() ?: {}
    }

    private fun anyInstant(): Instant {
        return any(Instant::class.java) ?: Instant.EPOCH
    }

    private fun anyLocalDate(): LocalDate {
        return any(LocalDate::class.java) ?: LocalDate.EPOCH
    }

    private fun anyIntValue(): Int {
        return anyInt()
    }

    private fun <T> eqValue(value: T): T {
        return eq(value) ?: value
    }

    private fun backfillJob(
        id: Long,
        status: ExchangeRateBackfillJobStatus,
    ): ExchangeRateBackfillJob {
        return ExchangeRateBackfillJob(
            requestedBy = 1L,
            fromDate = LocalDate.parse("2026-06-01"),
            toDate = LocalDate.parse("2026-06-02"),
            pauseBetweenRequestsMillis = 300L,
            status = status,
        ).also {
            it.id = id
        }
    }
}
