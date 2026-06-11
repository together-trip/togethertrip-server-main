package com.togethertrip.main.exchange.batch

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
            val targetDates = importService.findMissingRateDates(from, to)
            markRunning(
                backfillJobId = backfillJobId,
                totalRequestedDays = targetDates.size.toLong(),
            )
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
        requireUpdated(
            updatedRows = backfillJobRepository.updateBatchJobExecutionId(
                id = backfillJobId,
                batchJobExecutionId = batchJobExecutionId,
                updatedAt = Instant.now(),
            ),
            backfillJobId = backfillJobId,
        )
    }

    fun markLaunchFailed(
        backfillJobId: Long,
        exception: Exception,
    ) {
        markFailed(
            backfillJobId = backfillJobId,
            message = exception.message ?: exception::class.simpleName.orEmpty(),
        )
    }

    fun markFailedFromBatch(
        backfillJobId: Long,
        message: String?,
    ) {
        val now = Instant.now()
        val updatedRows = backfillJobRepository.markFailedIfNotTerminal(
            id = backfillJobId,
            status = ExchangeRateBackfillJobStatus.FAILED,
            lastErrorMessage = (message ?: "Spring Batch 백필 작업이 실패했습니다.").take(MAX_ERROR_MESSAGE_LENGTH),
            finishedAt = now,
            updatedAt = now,
            terminalStatuses = TERMINAL_STATUSES,
        )

        if (updatedRows > 0) {
            return
        }

        val job = backfillJobRepository.findByIdAndDeletedAtIsNull(backfillJobId)
            ?: throw IllegalArgumentException("백필 작업을 찾을 수 없습니다. id=$backfillJobId")
        if (job.status !in TERMINAL_STATUSES) {
            throw IllegalStateException("백필 실패 상태를 기록하지 못했습니다. id=$backfillJobId status=${job.status}")
        }
    }

    private fun markRunning(
        backfillJobId: Long,
        totalRequestedDays: Long,
    ) {
        val now = Instant.now()
        requireUpdated(
            updatedRows = backfillJobRepository.markRunning(
                id = backfillJobId,
                status = ExchangeRateBackfillJobStatus.RUNNING,
                totalRequestedDays = totalRequestedDays,
                startedAt = now,
                updatedAt = now,
            ),
            backfillJobId = backfillJobId,
        )
    }

    private fun recordResult(
        backfillJobId: Long,
        result: ExchangeRateImportResult,
    ) {
        val successIncrement: Int
        val failedIncrement: Int
        val noDataIncrement: Int
        val nonBusinessDayIncrement: Int

        when (result) {
            is ExchangeRateImportResult.Imported -> {
                successIncrement = 1
                failedIncrement = 0
                noDataIncrement = 0
                nonBusinessDayIncrement = 0
            }
            is ExchangeRateImportResult.NoData -> {
                successIncrement = 0
                failedIncrement = 0
                noDataIncrement = 1
                nonBusinessDayIncrement = 0
            }
            is ExchangeRateImportResult.NonBusinessDay -> {
                successIncrement = 0
                failedIncrement = 0
                noDataIncrement = 0
                nonBusinessDayIncrement = 1
            }
            is ExchangeRateImportResult.Failed,
            is ExchangeRateImportResult.Error,
            -> {
                successIncrement = 0
                failedIncrement = 1
                noDataIncrement = 0
                nonBusinessDayIncrement = 0
            }
        }

        requireUpdated(
            updatedRows = backfillJobRepository.incrementProgress(
                id = backfillJobId,
                successIncrement = successIncrement,
                failedIncrement = failedIncrement,
                noDataIncrement = noDataIncrement,
                nonBusinessDayIncrement = nonBusinessDayIncrement,
                updatedAt = Instant.now(),
            ),
            backfillJobId = backfillJobId,
        )
    }

    private fun markCompleted(backfillJobId: Long) {
        val now = Instant.now()
        requireUpdated(
            updatedRows = backfillJobRepository.markFinished(
                id = backfillJobId,
                status = ExchangeRateBackfillJobStatus.COMPLETED,
                finishedAt = now,
                updatedAt = now,
            ),
            backfillJobId = backfillJobId,
        )
    }

    private fun markSkippedLocked(backfillJobId: Long) {
        val now = Instant.now()
        requireUpdated(
            updatedRows = backfillJobRepository.markFailed(
                id = backfillJobId,
                status = ExchangeRateBackfillJobStatus.SKIPPED_LOCKED,
                lastErrorMessage = "환율 수집 lock을 획득하지 못했습니다.",
                finishedAt = now,
                updatedAt = now,
            ),
            backfillJobId = backfillJobId,
        )
    }

    private fun markFailed(
        backfillJobId: Long,
        message: String,
    ) {
        val now = Instant.now()
        requireUpdated(
            updatedRows = backfillJobRepository.markFailed(
                id = backfillJobId,
                status = ExchangeRateBackfillJobStatus.FAILED,
                lastErrorMessage = message.take(MAX_ERROR_MESSAGE_LENGTH),
                finishedAt = now,
                updatedAt = now,
            ),
            backfillJobId = backfillJobId,
        )
    }

    private fun requireUpdated(
        updatedRows: Int,
        backfillJobId: Long,
    ) {
        require(updatedRows > 0) {
            "백필 작업을 찾을 수 없습니다. id=$backfillJobId"
        }
    }

    companion object {
        private const val MAX_ERROR_MESSAGE_LENGTH = 500
        private val TERMINAL_STATUSES = listOf(
            ExchangeRateBackfillJobStatus.COMPLETED,
            ExchangeRateBackfillJobStatus.SKIPPED_LOCKED,
        )
    }
}
