package com.togethertrip.main.exchange.batch

import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJob
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJobStatus
import com.togethertrip.main.exchange.repository.ExchangeRateBackfillJobRepository
import com.togethertrip.main.exchange.service.ExchangeRateImportResult
import com.togethertrip.main.exchange.service.ExchangeRateImportService
import com.togethertrip.main.exchange.support.ExchangeRateDistributedLock
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Service
class ExchangeRateBackfillBatchService(
    private val importService: ExchangeRateImportService,
    private val distributedLock: ExchangeRateDistributedLock,
    private val backfillJobRepository: ExchangeRateBackfillJobRepository,
) {

    fun run(
        backfillJobId: Long,
        from: LocalDate,
        to: LocalDate,
        pauseBetweenRequests: Duration,
    ) {
        val executed = distributedLock.runIfAcquired {
            val job = findJob(backfillJobId)
            markRunning(job)

            val targetDates = importService.findMissingRateDates(from, to)
            targetDates.forEachIndexed { index, rateDate ->
                if (index > 0 && !pauseBetweenRequests.isZero && !pauseBetweenRequests.isNegative) {
                    Thread.sleep(pauseBetweenRequests.toMillis())
                }

                val result = importService.importByDate(rateDate)
                recordResult(backfillJobId, result)
            }

            markCompleted(backfillJobId)
        }

        if (executed == null) {
            markSkippedLocked(backfillJobId)
        }
    }

    fun markBatchExecution(
        backfillJobId: Long,
        batchJobExecutionId: Long,
    ) {
        val job = findJob(backfillJobId)
        job.batchJobExecutionId = batchJobExecutionId
        job.updatedAt = Instant.now()
        backfillJobRepository.save(job)
    }

    fun markLaunchFailed(
        backfillJobId: Long,
        exception: Exception,
    ) {
        val job = findJob(backfillJobId)
        markFailed(
            job = job,
            message = exception.message ?: exception::class.simpleName.orEmpty(),
        )
    }

    fun markFailedFromBatch(
        backfillJobId: Long,
        message: String?,
    ) {
        val job = findJob(backfillJobId)
        if (job.status == ExchangeRateBackfillJobStatus.COMPLETED ||
            job.status == ExchangeRateBackfillJobStatus.SKIPPED_LOCKED
        ) {
            return
        }

        markFailed(
            job = job,
            message = message ?: "Spring Batch 백필 작업이 실패했습니다.",
        )
    }

    private fun markRunning(job: ExchangeRateBackfillJob) {
        val now = Instant.now()
        job.status = ExchangeRateBackfillJobStatus.RUNNING
        job.startedAt = now
        job.finishedAt = null
        job.lastErrorMessage = null
        job.updatedAt = now
        backfillJobRepository.save(job)
    }

    private fun recordResult(
        backfillJobId: Long,
        result: ExchangeRateImportResult,
    ) {
        val job = findJob(backfillJobId)
        job.processedDays += 1
        when (result) {
            is ExchangeRateImportResult.Imported -> job.successCount += 1
            is ExchangeRateImportResult.NoData -> job.noDataCount += 1
            is ExchangeRateImportResult.NonBusinessDay -> job.nonBusinessDayCount += 1
            is ExchangeRateImportResult.Failed,
            is ExchangeRateImportResult.Error,
            -> job.failedCount += 1
        }
        job.updatedAt = Instant.now()
        backfillJobRepository.save(job)
    }

    private fun markCompleted(backfillJobId: Long) {
        val job = findJob(backfillJobId)
        val now = Instant.now()
        job.status = ExchangeRateBackfillJobStatus.COMPLETED
        job.finishedAt = now
        job.updatedAt = now
        backfillJobRepository.save(job)
    }

    private fun markSkippedLocked(backfillJobId: Long) {
        val job = findJob(backfillJobId)
        val now = Instant.now()
        job.status = ExchangeRateBackfillJobStatus.SKIPPED_LOCKED
        job.lastErrorMessage = "환율 수집 lock을 획득하지 못했습니다."
        job.finishedAt = now
        job.updatedAt = now
        backfillJobRepository.save(job)
    }

    private fun markFailed(
        job: ExchangeRateBackfillJob,
        message: String,
    ) {
        val now = Instant.now()
        job.status = ExchangeRateBackfillJobStatus.FAILED
        job.lastErrorMessage = message.take(MAX_ERROR_MESSAGE_LENGTH)
        job.finishedAt = now
        job.updatedAt = now
        backfillJobRepository.save(job)
    }

    private fun findJob(backfillJobId: Long): ExchangeRateBackfillJob {
        return backfillJobRepository.findById(backfillJobId)
            .orElseThrow { IllegalArgumentException("백필 작업을 찾을 수 없습니다. id=$backfillJobId") }
    }

    companion object {
        private const val MAX_ERROR_MESSAGE_LENGTH = 500
    }
}
