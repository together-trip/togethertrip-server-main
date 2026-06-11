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
        val parameters = contribution.stepExecution.jobParameters
        val backfillJobId = requireNotNull(
            parameters.getLong(ExchangeRateBackfillBatchConstants.PARAM_BACKFILL_JOB_ID)
        ) {
            "backfillJobId job parameter가 필요합니다."
        }
        val from = LocalDate.parse(
            requireNotNull(parameters.getString(ExchangeRateBackfillBatchConstants.PARAM_FROM)) {
                "from job parameter가 필요합니다."
            }
        )
        val to = LocalDate.parse(
            requireNotNull(parameters.getString(ExchangeRateBackfillBatchConstants.PARAM_TO)) {
                "to job parameter가 필요합니다."
            }
        )
        val pauseMillis = requireNotNull(
            parameters.getLong(ExchangeRateBackfillBatchConstants.PARAM_PAUSE_MILLIS)
        ) {
            "pauseMillis job parameter가 필요합니다."
        }

        backfillBatchService.run(
            backfillJobId = backfillJobId,
            from = from,
            to = to,
            pauseBetweenRequests = Duration.ofMillis(pauseMillis),
        )

        return RepeatStatus.FINISHED
    }
}
