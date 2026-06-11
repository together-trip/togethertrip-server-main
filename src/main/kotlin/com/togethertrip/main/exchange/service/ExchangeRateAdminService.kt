package com.togethertrip.main.exchange.service

import com.togethertrip.main.exchange.config.ExchangeRateProperties
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJob
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJobStatus
import com.togethertrip.main.exchange.domain.ExchangeRateImportRunStatus
import com.togethertrip.main.exchange.dto.request.ExchangeRateBackfillRequest
import com.togethertrip.main.exchange.dto.response.ExchangeRateBackfillJobResponse
import com.togethertrip.main.exchange.dto.response.ExchangeRateImportRunResponse
import com.togethertrip.main.exchange.repository.ExchangeRateBackfillJobRepository
import com.togethertrip.main.exchange.repository.ExchangeRateImportRunRepository
import com.togethertrip.main.exchange.support.ExchangeRateDistributedLock
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class ExchangeRateAdminService(
    private val properties: ExchangeRateProperties,
    private val importService: ExchangeRateImportService,
    private val distributedLock: ExchangeRateDistributedLock,
    private val importRunRepository: ExchangeRateImportRunRepository,
    private val backfillJobRepository: ExchangeRateBackfillJobRepository,
) {

    fun getImportRuns(
        from: LocalDate,
        to: LocalDate,
        status: ExchangeRateImportRunStatus?,
    ): List<ExchangeRateImportRunResponse> {
        require(!from.isAfter(to)) {
            "from은 to보다 이후일 수 없습니다."
        }

        return importRunRepository
            .findByProviderAndRateDateBetweenAndDeletedAtIsNullOrderByRateDateAsc(
                provider = PROVIDER,
                from = from,
                to = to,
            )
            .asSequence()
            .filter { run -> status == null || run.status == status }
            .map(ExchangeRateImportRunResponse::from)
            .toList()
    }

    fun getBackfillJobs(limit: Int): List<ExchangeRateBackfillJobResponse> {
        val size = limit.coerceIn(1, MAX_JOB_LIST_LIMIT)
        return backfillJobRepository
            .findByDeletedAtIsNullOrderByCreatedAtDesc(PageRequest.of(0, size))
            .map(ExchangeRateBackfillJobResponse::from)
    }

    fun runBackfill(
        requestedBy: Long,
        request: ExchangeRateBackfillRequest,
    ): ExchangeRateBackfillJobResponse {
        require(!request.from.isAfter(request.to)) {
            "from은 to보다 이후일 수 없습니다."
        }

        val maxDaysPerRun = request.maxDaysPerRun ?: properties.backfill.maxDaysPerRun
        require(maxDaysPerRun > 0) {
            "maxDaysPerRun은 0보다 커야 합니다."
        }

        val pauseMillis = request.pauseBetweenRequestsMillis
            ?: properties.backfill.pauseBetweenRequests.toMillis()
        require(pauseMillis >= 0) {
            "pauseBetweenRequestsMillis는 0 이상이어야 합니다."
        }

        val now = Instant.now()
        val job = backfillJobRepository.save(
            ExchangeRateBackfillJob(
                requestedBy = requestedBy,
                fromDate = request.from,
                toDate = request.to,
                maxDaysPerRun = maxDaysPerRun,
                pauseBetweenRequestsMillis = pauseMillis,
                totalRequestedDays = ChronoUnit.DAYS.between(request.from, request.to) + 1,
                status = ExchangeRateBackfillJobStatus.REQUESTED,
            ).also {
                it.createdAt = now
                it.updatedAt = now
            }
        )

        val results = try {
            distributedLock.runIfAcquired {
                markRunning(job)
                importService.importMissingRates(
                    from = job.fromDate,
                    to = job.toDate,
                    maxDays = job.maxDaysPerRun,
                    pauseBetweenRequests = Duration.ofMillis(job.pauseBetweenRequestsMillis),
                )
            }
        } catch (exception: Exception) {
            markFailed(job, exception)
            return ExchangeRateBackfillJobResponse.from(job)
        }

        if (results == null) {
            markSkippedLocked(job)
            return ExchangeRateBackfillJobResponse.from(job)
        }

        markCompleted(job, results)
        return ExchangeRateBackfillJobResponse.from(job)
    }

    private fun markRunning(job: ExchangeRateBackfillJob) {
        val now = Instant.now()
        job.status = ExchangeRateBackfillJobStatus.RUNNING
        job.startedAt = now
        job.updatedAt = now
        backfillJobRepository.save(job)
    }

    private fun markCompleted(
        job: ExchangeRateBackfillJob,
        results: List<ExchangeRateImportResult>,
    ) {
        val now = Instant.now()
        job.status = ExchangeRateBackfillJobStatus.COMPLETED
        job.processedDays = results.size
        job.successCount = results.count { it is ExchangeRateImportResult.Imported }
        job.failedCount = results.count {
            it is ExchangeRateImportResult.Failed || it is ExchangeRateImportResult.Error
        }
        job.noDataCount = results.count { it is ExchangeRateImportResult.NoData }
        job.nonBusinessDayCount = results.count { it is ExchangeRateImportResult.NonBusinessDay }
        job.finishedAt = now
        job.updatedAt = now
        backfillJobRepository.save(job)
    }

    private fun markSkippedLocked(job: ExchangeRateBackfillJob) {
        val now = Instant.now()
        job.status = ExchangeRateBackfillJobStatus.SKIPPED_LOCKED
        job.lastErrorMessage = "환율 수집 lock을 획득하지 못했습니다."
        job.finishedAt = now
        job.updatedAt = now
        backfillJobRepository.save(job)
    }

    private fun markFailed(
        job: ExchangeRateBackfillJob,
        exception: Exception,
    ) {
        val now = Instant.now()
        job.status = ExchangeRateBackfillJobStatus.FAILED
        job.lastErrorMessage = (exception.message ?: exception::class.simpleName.orEmpty())
            .take(MAX_ERROR_MESSAGE_LENGTH)
        job.finishedAt = now
        job.updatedAt = now
        backfillJobRepository.save(job)
    }

    companion object {
        private const val PROVIDER = "KOREA_EXIM"
        private const val MAX_JOB_LIST_LIMIT = 100
        private const val MAX_ERROR_MESSAGE_LENGTH = 500
    }
}
