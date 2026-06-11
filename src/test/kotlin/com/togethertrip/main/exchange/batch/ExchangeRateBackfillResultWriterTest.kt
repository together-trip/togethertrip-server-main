package com.togethertrip.main.exchange.batch

import com.togethertrip.main.exchange.service.ExchangeRateImportResult
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.Chunk
import java.time.LocalDate

class ExchangeRateBackfillResultWriterTest {

    private val backfillBatchService = mock(ExchangeRateBackfillBatchService::class.java)
    private val writer = ExchangeRateBackfillResultWriter(backfillBatchService)

    @Test
    fun `chunk 결과를 백필 진행률 원장에 기록한다`() {
        val result = ExchangeRateImportResult.NonBusinessDay(LocalDate.parse("2026-06-06"))

        writer.beforeStep(stepExecution())
        writer.write(Chunk(listOf(result)))

        verify(backfillBatchService).recordResults(10L, listOf(result))
    }

    private fun stepExecution(): StepExecution {
        val parameters = JobParametersBuilder()
            .addLong(ExchangeRateBackfillBatchConstants.PARAM_BACKFILL_JOB_ID, 10L)
            .toJobParameters()
        return StepExecution(
            1L,
            ExchangeRateBackfillBatchConstants.STEP_NAME,
            JobExecution(1L, JobInstance(1L, ExchangeRateBackfillBatchConstants.JOB_NAME), parameters),
        )
    }
}
