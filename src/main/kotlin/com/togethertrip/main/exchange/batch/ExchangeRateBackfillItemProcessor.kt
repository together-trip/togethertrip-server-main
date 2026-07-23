package com.togethertrip.main.exchange.batch

import com.togethertrip.main.exchange.service.ExchangeRateImportResult
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate

@Component
@StepScope
class ExchangeRateBackfillItemProcessor(
    private val backfillBatchService: ExchangeRateBackfillBatchService,
) : ItemProcessor<LocalDate, ExchangeRateImportResult>, StepExecutionListener {

    private var pauseBetweenRequests: Duration = Duration.ZERO
    private var processedCount: Long = 0L

    override fun beforeStep(stepExecution: StepExecution) {
        val pauseMillis = requireNotNull(
            stepExecution.jobParameters.getLong(ExchangeRateBackfillBatchConstants.PARAM_PAUSE_MILLIS)
        ) {
            "pauseMillis job parameter가 필요합니다."
        }
        pauseBetweenRequests = Duration.ofMillis(pauseMillis)
        processedCount = 0L
    }

    override fun process(item: LocalDate): ExchangeRateImportResult {
        if (processedCount > 0 && !pauseBetweenRequests.isZero && !pauseBetweenRequests.isNegative) {
            Thread.sleep(pauseBetweenRequests.toMillis())
        }

        processedCount += 1
        return backfillBatchService.importByDate(item)
    }
}
