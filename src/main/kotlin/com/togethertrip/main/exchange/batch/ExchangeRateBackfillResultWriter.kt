package com.togethertrip.main.exchange.batch

import com.togethertrip.main.exchange.service.ExchangeRateImportResult
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.stereotype.Component

@Component
@StepScope
class ExchangeRateBackfillResultWriter(
    private val backfillBatchService: ExchangeRateBackfillBatchService,
) : ItemWriter<ExchangeRateImportResult>, StepExecutionListener {

    private var backfillJobId: Long = 0L

    override fun beforeStep(stepExecution: StepExecution) {
        backfillJobId = requireNotNull(
            stepExecution.jobParameters.getLong(ExchangeRateBackfillBatchConstants.PARAM_BACKFILL_JOB_ID)
        ) {
            "backfillJobId job parameter가 필요합니다."
        }
    }

    override fun write(chunk: Chunk<out ExchangeRateImportResult>) {
        backfillBatchService.recordResults(backfillJobId, chunk.items)
    }
}
