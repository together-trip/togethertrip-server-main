package com.togethertrip.main.exchange.batch

import com.togethertrip.main.exchange.service.ExchangeRateImportResult
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.step.StepExecution
import java.time.LocalDate
import kotlin.test.assertEquals

class ExchangeRateBackfillItemProcessorTest {

    private val backfillBatchService = mock(ExchangeRateBackfillBatchService::class.java)
    private val processor = ExchangeRateBackfillItemProcessor(backfillBatchService)

    @Test
    fun `날짜 item을 수집 결과로 변환한다`() {
        val rateDate = LocalDate.parse("2026-06-01")
        val result = ExchangeRateImportResult.NoData(rateDate)
        `when`(backfillBatchService.importByDate(rateDate)).thenReturn(result)

        processor.beforeStep(stepExecution())

        assertEquals(result, processor.process(rateDate))
        verify(backfillBatchService).importByDate(rateDate)
    }

    private fun stepExecution(): StepExecution {
        val parameters = JobParametersBuilder()
            .addLong(ExchangeRateBackfillBatchConstants.PARAM_PAUSE_MILLIS, 0L)
            .toJobParameters()
        return StepExecution(
            1L,
            ExchangeRateBackfillBatchConstants.STEP_NAME,
            JobExecution(1L, JobInstance(1L, ExchangeRateBackfillBatchConstants.JOB_NAME), parameters),
        )
    }
}
