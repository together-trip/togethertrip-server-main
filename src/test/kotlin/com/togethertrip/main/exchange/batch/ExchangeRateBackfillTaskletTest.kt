package com.togethertrip.main.exchange.batch

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.test.MetaDataInstanceFactory
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals

class ExchangeRateBackfillTaskletTest {

    private val backfillBatchService = mock(ExchangeRateBackfillBatchService::class.java)
    private val tasklet = ExchangeRateBackfillTasklet(backfillBatchService)

    @Test
    fun `JobParameters에서 백필 실행 인자를 읽어 service에 전달한다`() {
        val jobParameters = JobParametersBuilder()
            .addLong(ExchangeRateBackfillBatchConstants.PARAM_BACKFILL_JOB_ID, 10L)
            .addString(ExchangeRateBackfillBatchConstants.PARAM_FROM, "2026-06-01")
            .addString(ExchangeRateBackfillBatchConstants.PARAM_TO, "2026-06-10")
            .addLong(ExchangeRateBackfillBatchConstants.PARAM_PAUSE_MILLIS, 300L)
            .toJobParameters()
        val stepExecution = MetaDataInstanceFactory.createStepExecution(jobParameters)
        val contribution = StepContribution(stepExecution)

        val result = tasklet.execute(
            contribution = contribution,
            chunkContext = mock(ChunkContext::class.java),
        )

        assertEquals(org.springframework.batch.infrastructure.repeat.RepeatStatus.FINISHED, result)
        verify(backfillBatchService).run(
            backfillJobId = 10L,
            from = LocalDate.parse("2026-06-01"),
            to = LocalDate.parse("2026-06-10"),
            pauseBetweenRequests = Duration.ofMillis(300),
        )
    }
}
