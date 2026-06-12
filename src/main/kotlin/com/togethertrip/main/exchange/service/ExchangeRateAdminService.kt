package com.togethertrip.main.exchange.service

import com.togethertrip.main.exchange.batch.ExchangeRateBackfillBatchConstants
import com.togethertrip.main.exchange.batch.ExchangeRateBackfillBatchService
import com.togethertrip.main.exchange.config.ExchangeRateProperties
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJob
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJobStatus
import com.togethertrip.main.exchange.domain.ExchangeRateImportRunStatus
import com.togethertrip.main.exchange.dto.request.ExchangeRateBackfillRequest
import com.togethertrip.main.exchange.dto.response.ExchangeRateBackfillJobResponse
import com.togethertrip.main.exchange.dto.response.ExchangeRateImportRunResponse
import com.togethertrip.main.exchange.repository.ExchangeRateBackfillJobRepository
import com.togethertrip.main.exchange.repository.ExchangeRateImportRunRepository
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class ExchangeRateAdminService(
    private val properties: ExchangeRateProperties,
    @Qualifier("jobOperator")
    private val jobOperator: JobOperator,
    @Qualifier("exchangeRateBackfillJob")
    private val exchangeRateBackfillJob: Job,
    private val backfillBatchService: ExchangeRateBackfillBatchService,
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

    fun getBackfillJob(id: Long): ExchangeRateBackfillJobResponse {
        val job = backfillJobRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw IllegalArgumentException("백필 작업을 찾을 수 없습니다. id=$id")

        return ExchangeRateBackfillJobResponse.from(job)
    }

    fun runBackfill(
        requestedBy: Long,
        request: ExchangeRateBackfillRequest,
    ): ExchangeRateBackfillJobResponse {
        require(!request.from.isAfter(request.to)) {
            "from은 to보다 이후일 수 없습니다."
        }

        val pauseMillis = properties.backfill.pauseBetweenRequests.toMillis()
        val now = Instant.now()
        var lastSaveFailure: DataIntegrityViolationException? = null

        repeat(ACTIVE_SAVE_RETRY_COUNT) {
            findReusableActiveBackfillJob(now)?.let { activeJob ->
                return ExchangeRateBackfillJobResponse.from(activeJob)
            }

            val job = createBackfillJob(
                requestedBy = requestedBy,
                request = request,
                pauseMillis = pauseMillis,
                now = now,
            )

            try {
                return launchBackfillJob(
                    job = backfillJobRepository.saveAndFlush(job),
                    requestedAt = now,
                )
            } catch (exception: DataIntegrityViolationException) {
                lastSaveFailure = exception
            }
        }

        findReusableActiveBackfillJob(Instant.now())?.let { activeJob ->
            return ExchangeRateBackfillJobResponse.from(activeJob)
        }
        throw lastSaveFailure ?: IllegalStateException("백필 작업 생성에 실패했습니다.")
    }

    private fun createBackfillJob(
        requestedBy: Long,
        request: ExchangeRateBackfillRequest,
        pauseMillis: Long,
        now: Instant,
    ): ExchangeRateBackfillJob {
        return ExchangeRateBackfillJob(
            requestedBy = requestedBy,
            fromDate = request.from,
            toDate = request.to,
            pauseBetweenRequestsMillis = pauseMillis,
            totalRequestedDays = ChronoUnit.DAYS.between(request.from, request.to) + 1,
            status = ExchangeRateBackfillJobStatus.REQUESTED,
        ).also {
            it.createdAt = now
            it.updatedAt = now
        }
    }

    private fun launchBackfillJob(
        job: ExchangeRateBackfillJob,
        requestedAt: Instant,
    ): ExchangeRateBackfillJobResponse {
        val parameters = JobParametersBuilder()
            .addLong(ExchangeRateBackfillBatchConstants.PARAM_BACKFILL_JOB_ID, job.id)
            .addString(ExchangeRateBackfillBatchConstants.PARAM_FROM, job.fromDate.toString())
            .addString(ExchangeRateBackfillBatchConstants.PARAM_TO, job.toDate.toString())
            .addLong(ExchangeRateBackfillBatchConstants.PARAM_PAUSE_MILLIS, job.pauseBetweenRequestsMillis)
            .addLong(ExchangeRateBackfillBatchConstants.PARAM_REQUESTED_AT, requestedAt.toEpochMilli())
            .toJobParameters()

        try {
            val execution = jobOperator.start(exchangeRateBackfillJob, parameters)
            val executionId = execution.id
                ?: throw IllegalStateException("Spring Batch JobExecution id가 없습니다.")

            backfillBatchService.markBatchExecution(
                backfillJobId = job.id,
                batchJobExecutionId = executionId,
            )
        } catch (exception: Exception) {
            backfillBatchService.markLaunchFailed(job.id, exception)
            val failedJob = backfillJobRepository.findByIdAndDeletedAtIsNull(job.id) ?: job
            return ExchangeRateBackfillJobResponse.from(failedJob)
        }

        val launchedJob = backfillJobRepository.findByIdAndDeletedAtIsNull(job.id) ?: job
        return ExchangeRateBackfillJobResponse.from(launchedJob)
    }

    private fun findReusableActiveBackfillJob(now: Instant): ExchangeRateBackfillJob? {
        val activeJob = backfillJobRepository.findFirstByStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
            ACTIVE_BACKFILL_STATUSES
        ) ?: return null

        if (activeJob.isStaleUnlaunchedRequest(now)) {
            backfillBatchService.markLaunchFailed(
                backfillJobId = activeJob.id,
                exception = IllegalStateException("Batch 실행 전 REQUESTED 상태로 오래 남은 백필 작업입니다."),
            )
            return null
        }

        return activeJob
    }

    private fun ExchangeRateBackfillJob.isStaleUnlaunchedRequest(now: Instant): Boolean {
        return status == ExchangeRateBackfillJobStatus.REQUESTED &&
            batchJobExecutionId == null &&
            createdAt.isBefore(now.minus(STALE_REQUESTED_TIMEOUT))
    }

    companion object {
        private const val PROVIDER = "KOREA_EXIM"
        private const val MAX_JOB_LIST_LIMIT = 100
        private const val ACTIVE_SAVE_RETRY_COUNT = 2
        private val STALE_REQUESTED_TIMEOUT = Duration.ofMinutes(1)
        private val ACTIVE_BACKFILL_STATUSES = listOf(
            ExchangeRateBackfillJobStatus.REQUESTED,
            ExchangeRateBackfillJobStatus.RUNNING,
        )
    }
}
