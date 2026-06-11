package com.togethertrip.main.exchange.batch

import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.JobExecutionListener
import org.springframework.stereotype.Component

@Component
class ExchangeRateBackfillJobListener(
    private val backfillBatchService: ExchangeRateBackfillBatchService,
) : JobExecutionListener {

    override fun afterJob(jobExecution: JobExecution) {
        if (jobExecution.status != BatchStatus.FAILED) {
            return
        }

        val backfillJobId = jobExecution.jobParameters
            .getLong(ExchangeRateBackfillBatchConstants.PARAM_BACKFILL_JOB_ID)
            ?: return

        backfillBatchService.markFailedFromBatch(
            backfillJobId = backfillJobId,
            message = jobExecution.allFailureExceptions.firstOrNull()?.message,
        )
    }
}
