package com.togethertrip.main.exchange.batch

import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate

@Component
class ExchangeRateBackfillTasklet(
    private val backfillBatchService: ExchangeRateBackfillBatchService,
) : Tasklet {

    override fun execute(
        contribution: StepContribution,
        chunkContext: ChunkContext,
    ): RepeatStatus {
        val parameters = chunkContext.stepContext.jobParameters
        val backfillJobId = parameters[ExchangeRateBackfillBatchConstants.PARAM_BACKFILL_JOB_ID].toString().toLong()
        val from = LocalDate.parse(parameters[ExchangeRateBackfillBatchConstants.PARAM_FROM].toString())
        val to = LocalDate.parse(parameters[ExchangeRateBackfillBatchConstants.PARAM_TO].toString())
        val pauseMillis = parameters[ExchangeRateBackfillBatchConstants.PARAM_PAUSE_MILLIS].toString().toLong()

        backfillBatchService.run(
            backfillJobId = backfillJobId,
            from = from,
            to = to,
            pauseBetweenRequests = Duration.ofMillis(pauseMillis),
        )

        return RepeatStatus.FINISHED
    }
}
