package com.togethertrip.main.exchange.service

import com.togethertrip.main.exchange.batch.ExchangeRateBackfillBatchService
import com.togethertrip.main.exchange.config.ExchangeRateProperties
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJob
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJobStatus
import com.togethertrip.main.exchange.dto.request.ExchangeRateBackfillRequest
import com.togethertrip.main.exchange.repository.ExchangeRateBackfillJobRepository
import com.togethertrip.main.exchange.repository.ExchangeRateImportRunRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.batch.core.launch.JobOperator
import org.springframework.dao.DataIntegrityViolationException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

class ExchangeRateAdminServiceTest {

    private lateinit var properties: ExchangeRateProperties
    private lateinit var jobOperator: JobOperator
    private lateinit var exchangeRateBackfillJob: Job
    private lateinit var backfillBatchService: ExchangeRateBackfillBatchService
    private lateinit var importRunRepository: ExchangeRateImportRunRepository
    private lateinit var backfillJobRepository: ExchangeRateBackfillJobRepository
    private lateinit var service: ExchangeRateAdminService

    @BeforeEach
    fun setUp() {
        properties = ExchangeRateProperties().apply {
            backfill.pauseBetweenRequests = Duration.ofMillis(300)
        }
        jobOperator = mock(JobOperator::class.java)
        exchangeRateBackfillJob = mock(Job::class.java)
        backfillBatchService = mock(ExchangeRateBackfillBatchService::class.java)
        importRunRepository = mock(ExchangeRateImportRunRepository::class.java)
        backfillJobRepository = mock(ExchangeRateBackfillJobRepository::class.java)
        service = ExchangeRateAdminService(
            properties = properties,
            jobOperator = jobOperator,
            exchangeRateBackfillJob = exchangeRateBackfillJob,
            backfillBatchService = backfillBatchService,
            importRunRepository = importRunRepository,
            backfillJobRepository = backfillJobRepository,
        )
    }

    @Test
    fun `진행 중인 백필이 있으면 새 job을 생성하지 않고 기존 job을 반환한다`() {
        val activeJob = backfillJob(id = 10L, status = ExchangeRateBackfillJobStatus.RUNNING)
        `when`(
            backfillJobRepository.findFirstByStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
                ACTIVE_STATUSES
            )
        ).thenReturn(activeJob)

        val response = service.runBackfill(
            requestedBy = 1L,
            request = backfillRequest(),
        )

        assertEquals(activeJob.id, response.id)
        assertEquals(ExchangeRateBackfillJobStatus.RUNNING, response.status)
        verify(backfillJobRepository, never()).saveAndFlush(any(ExchangeRateBackfillJob::class.java))
        verify(jobOperator, never()).start(any(Job::class.java), any(JobParameters::class.java))
    }

    @Test
    fun `active unique 제약 충돌이 발생하면 기존 active job을 반환한다`() {
        val activeJob = backfillJob(id = 11L, status = ExchangeRateBackfillJobStatus.REQUESTED)
        `when`(
            backfillJobRepository.findFirstByStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
                ACTIVE_STATUSES
            )
        ).thenReturn(null, activeJob)
        `when`(backfillJobRepository.saveAndFlush(any(ExchangeRateBackfillJob::class.java)))
            .thenThrow(DataIntegrityViolationException("active backfill exists"))

        val response = service.runBackfill(
            requestedBy = 1L,
            request = backfillRequest(),
        )

        assertEquals(activeJob.id, response.id)
        assertEquals(ExchangeRateBackfillJobStatus.REQUESTED, response.status)
        verify(jobOperator, never()).start(any(Job::class.java), any(JobParameters::class.java))
    }

    @Test
    fun `Batch 실행 전 오래 남은 requested job은 실패 처리하고 새 job을 실행한다`() {
        val staleJob = backfillJob(id = 11L, status = ExchangeRateBackfillJobStatus.REQUESTED).also {
            it.createdAt = Instant.now().minus(Duration.ofMinutes(2))
            it.batchJobExecutionId = null
        }
        val savedJob = backfillJob(id = 12L, status = ExchangeRateBackfillJobStatus.REQUESTED)
        val execution = JobExecution(
            100L,
            JobInstance(1L, "exchangeRateBackfillJob"),
            JobParameters(),
        )

        `when`(
            backfillJobRepository.findFirstByStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
                ACTIVE_STATUSES
            )
        ).thenReturn(staleJob)
        `when`(backfillJobRepository.saveAndFlush(any(ExchangeRateBackfillJob::class.java))).thenReturn(savedJob)
        `when`(jobOperator.start(eq(exchangeRateBackfillJob), any(JobParameters::class.java))).thenReturn(execution)
        `when`(backfillJobRepository.findByIdAndDeletedAtIsNull(savedJob.id)).thenReturn(savedJob)

        val response = service.runBackfill(
            requestedBy = 1L,
            request = backfillRequest(),
        )

        assertEquals(savedJob.id, response.id)
        verify(backfillBatchService).markLaunchFailed(
            eq(staleJob.id),
            anyException(),
        )
        verify(backfillBatchService).markBatchExecution(
            backfillJobId = savedJob.id,
            batchJobExecutionId = 100L,
        )
    }

    @Test
    fun `active job이 없으면 백필 job을 저장하고 Batch를 실행한다`() {
        val savedJob = backfillJob(id = 12L, status = ExchangeRateBackfillJobStatus.REQUESTED)
        val launchedJob = backfillJob(id = 12L, status = ExchangeRateBackfillJobStatus.REQUESTED).also {
            it.batchJobExecutionId = 100L
        }
        val execution = JobExecution(
            100L,
            JobInstance(1L, "exchangeRateBackfillJob"),
            JobParameters(),
        )

        `when`(
            backfillJobRepository.findFirstByStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
                ACTIVE_STATUSES
            )
        ).thenReturn(null)
        `when`(backfillJobRepository.saveAndFlush(any(ExchangeRateBackfillJob::class.java))).thenReturn(savedJob)
        `when`(jobOperator.start(eq(exchangeRateBackfillJob), any(JobParameters::class.java))).thenReturn(execution)
        `when`(backfillJobRepository.findByIdAndDeletedAtIsNull(savedJob.id)).thenReturn(launchedJob)

        val response = service.runBackfill(
            requestedBy = 1L,
            request = backfillRequest(),
        )

        assertEquals(savedJob.id, response.id)
        assertEquals(100L, response.batchJobExecutionId)
        verify(backfillBatchService).markBatchExecution(
            backfillJobId = savedJob.id,
            batchJobExecutionId = 100L,
        )
    }

    private fun backfillRequest(): ExchangeRateBackfillRequest {
        return ExchangeRateBackfillRequest(
            from = LocalDate.parse("2026-06-01"),
            to = LocalDate.parse("2026-06-10"),
        )
    }

    private fun backfillJob(
        id: Long,
        status: ExchangeRateBackfillJobStatus,
    ): ExchangeRateBackfillJob {
        return ExchangeRateBackfillJob(
            requestedBy = 1L,
            fromDate = LocalDate.parse("2026-06-01"),
            toDate = LocalDate.parse("2026-06-10"),
            pauseBetweenRequestsMillis = 300L,
            status = status,
            totalRequestedDays = 10,
        ).also {
            it.id = id
        }
    }

    private fun anyException(): Exception {
        return any(Exception::class.java) ?: RuntimeException()
    }

    companion object {
        private val ACTIVE_STATUSES = listOf(
            ExchangeRateBackfillJobStatus.REQUESTED,
            ExchangeRateBackfillJobStatus.RUNNING,
        )
    }
}
