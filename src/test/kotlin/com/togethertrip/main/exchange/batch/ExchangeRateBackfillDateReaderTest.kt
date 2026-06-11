package com.togethertrip.main.exchange.batch

import com.togethertrip.main.exchange.support.ExchangeRateDistributedLock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.step.StepExecution
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExchangeRateBackfillDateReaderTest {

    private val backfillBatchService = mock(ExchangeRateBackfillBatchService::class.java)
    private val distributedLock = mock(ExchangeRateDistributedLock::class.java)
    private val reader = ExchangeRateBackfillDateReader(backfillBatchService, distributedLock)

    @Test
    fun `lock을 획득하면 미완료 날짜를 순서대로 읽고 완료 시 completed로 마감한다`() {
        val stepExecution = stepExecution()
        val firstDate = LocalDate.parse("2026-06-01")
        val secondDate = LocalDate.parse("2026-06-02")

        `when`(distributedLock.tryAcquireForCurrentThread()).thenReturn(true)
        `when`(
            backfillBatchService.prepareTargetDates(
                backfillJobId = 10L,
                from = LocalDate.parse("2026-06-01"),
                to = LocalDate.parse("2026-06-03"),
            )
        ).thenReturn(listOf(firstDate, secondDate))

        reader.beforeStep(stepExecution)

        assertEquals(firstDate, reader.read())
        assertEquals(secondDate, reader.read())
        assertNull(reader.read())

        stepExecution.status = BatchStatus.COMPLETED
        reader.afterStep(stepExecution)

        verify(backfillBatchService).markCompleted(10L)
        verify(distributedLock).releaseForCurrentThreadIfHeld()
    }

    @Test
    fun `lock 획득에 실패하면 skipped locked로 마감하고 읽을 날짜를 제공하지 않는다`() {
        val stepExecution = stepExecution()

        `when`(distributedLock.tryAcquireForCurrentThread()).thenReturn(false)

        reader.beforeStep(stepExecution)

        assertNull(reader.read())
        reader.afterStep(stepExecution)

        verify(backfillBatchService).markSkippedLocked(10L)
        verify(backfillBatchService, never()).prepareTargetDates(
            backfillJobId = 10L,
            from = LocalDate.parse("2026-06-01"),
            to = LocalDate.parse("2026-06-03"),
        )
        verify(distributedLock, never()).releaseForCurrentThreadIfHeld()
    }

    @Test
    fun `lock 획득 후 날짜 준비가 실패하면 lock을 해제하고 예외를 전달한다`() {
        val stepExecution = stepExecution()

        `when`(distributedLock.tryAcquireForCurrentThread()).thenReturn(true)
        `when`(
            backfillBatchService.prepareTargetDates(
                backfillJobId = 10L,
                from = LocalDate.parse("2026-06-01"),
                to = LocalDate.parse("2026-06-03"),
            )
        ).thenThrow(IllegalStateException("prepare failed"))

        assertThrows<IllegalStateException> {
            reader.beforeStep(stepExecution)
        }

        verify(distributedLock).releaseForCurrentThreadIfHeld()
    }

    private fun stepExecution(): StepExecution {
        val parameters = JobParametersBuilder()
            .addLong(ExchangeRateBackfillBatchConstants.PARAM_BACKFILL_JOB_ID, 10L)
            .addString(ExchangeRateBackfillBatchConstants.PARAM_FROM, "2026-06-01")
            .addString(ExchangeRateBackfillBatchConstants.PARAM_TO, "2026-06-03")
            .addLong(ExchangeRateBackfillBatchConstants.PARAM_PAUSE_MILLIS, 0L)
            .toJobParameters()
        return StepExecution(
            1L,
            ExchangeRateBackfillBatchConstants.STEP_NAME,
            JobExecution(1L, JobInstance(1L, ExchangeRateBackfillBatchConstants.JOB_NAME), parameters),
        )
    }
}
