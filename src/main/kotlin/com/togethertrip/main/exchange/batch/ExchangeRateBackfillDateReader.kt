package com.togethertrip.main.exchange.batch

import com.togethertrip.main.exchange.support.ExchangeRateDistributedLock
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
@StepScope
class ExchangeRateBackfillDateReader(
    private val backfillBatchService: ExchangeRateBackfillBatchService,
    private val distributedLock: ExchangeRateDistributedLock,
) : ItemReader<LocalDate>, StepExecutionListener {

    private var backfillJobId: Long = 0L
    private var dates: Iterator<LocalDate> = emptyList<LocalDate>().iterator()
    private var lockAcquired: Boolean = false

    override fun beforeStep(stepExecution: StepExecution) {
        val parameters = stepExecution.jobParameters
        backfillJobId = requireNotNull(
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

        lockAcquired = distributedLock.tryAcquireForCurrentThread()
        if (!lockAcquired) {
            backfillBatchService.markSkippedLocked(backfillJobId)
            dates = emptyList<LocalDate>().iterator()
            return
        }

        try {
            dates = backfillBatchService
                .prepareTargetDates(backfillJobId = backfillJobId, from = from, to = to)
                .iterator()
        } catch (exception: Exception) {
            distributedLock.releaseForCurrentThreadIfHeld()
            lockAcquired = false
            dates = emptyList<LocalDate>().iterator()
            throw exception
        }
    }

    override fun read(): LocalDate? {
        return if (dates.hasNext()) dates.next() else null
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus? {
        try {
            if (lockAcquired && stepExecution.status != BatchStatus.FAILED) {
                backfillBatchService.markCompleted(backfillJobId)
            }
        } finally {
            if (lockAcquired) {
                distributedLock.releaseForCurrentThreadIfHeld()
                lockAcquired = false
            }
            dates = emptyList<LocalDate>().iterator()
        }
        return stepExecution.exitStatus
    }
}
