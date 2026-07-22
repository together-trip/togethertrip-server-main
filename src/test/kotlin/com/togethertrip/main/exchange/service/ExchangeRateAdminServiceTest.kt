package com.togethertrip.main.exchange.service

import com.togethertrip.main.exchange.batch.ExchangeRateBackfillBatchService
import com.togethertrip.main.exchange.config.ExchangeRateProperties
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJob
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJobStatus
import com.togethertrip.main.exchange.domain.ExchangeRateImportRun
import com.togethertrip.main.exchange.domain.ExchangeRateImportRunStatus
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
import org.springframework.data.domain.PageRequest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun `환율 수집 이력은 날짜 범위를 검증하고 상태로 필터링한다`() {
        val success = importRun(1L, "2026-06-01", ExchangeRateImportRunStatus.SUCCESS)
        val failed = importRun(2L, "2026-06-02", ExchangeRateImportRunStatus.FAILED)
        `when`(
            importRunRepository.findByProviderAndRateDateBetweenAndDeletedAtIsNullOrderByRateDateAsc(
                "KOREA_EXIM",
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-02"),
            )
        ).thenReturn(listOf(success, failed))

        val all = service.getImportRuns(
            LocalDate.parse("2026-06-01"),
            LocalDate.parse("2026-06-02"),
            null,
        )
        val failures = service.getImportRuns(
            LocalDate.parse("2026-06-01"),
            LocalDate.parse("2026-06-02"),
            ExchangeRateImportRunStatus.FAILED,
        )

        assertEquals(listOf(1L, 2L), all.map { it.id })
        assertEquals(listOf(2L), failures.map { it.id })
        assertFailsWith<IllegalArgumentException> {
            service.getImportRuns(
                LocalDate.parse("2026-06-03"),
                LocalDate.parse("2026-06-02"),
                null,
            )
        }
    }

    @Test
    fun `백필 목록 limit은 최소 최대 범위로 보정한다`() {
        val job = backfillJob(10L, ExchangeRateBackfillJobStatus.RUNNING)
        `when`(backfillJobRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(PageRequest.of(0, 1)))
            .thenReturn(listOf(job))
        `when`(backfillJobRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(PageRequest.of(0, 100)))
            .thenReturn(listOf(job))

        assertEquals(listOf(10L), service.getBackfillJobs(0).map { it.id })
        assertEquals(listOf(10L), service.getBackfillJobs(101).map { it.id })
    }

    @Test
    fun `백필 단건은 존재 여부를 구분한다`() {
        val job = backfillJob(10L, ExchangeRateBackfillJobStatus.RUNNING)
        `when`(backfillJobRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(job)

        assertEquals(10L, service.getBackfillJob(10L).id)
        assertFailsWith<IllegalArgumentException> { service.getBackfillJob(999L) }
    }

    @Test
    fun `역전된 백필 날짜는 job 생성 전에 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            service.runBackfill(
                1L,
                ExchangeRateBackfillRequest(
                    from = LocalDate.parse("2026-06-02"),
                    to = LocalDate.parse("2026-06-01"),
                ),
            )
        }
        verify(backfillJobRepository, never()).saveAndFlush(any(ExchangeRateBackfillJob::class.java))
    }

    @Test
    fun `Batch 실행 예외는 실패 처리 후 저장된 실패 상태를 반환한다`() {
        val saved = backfillJob(12L, ExchangeRateBackfillJobStatus.REQUESTED)
        val failed = backfillJob(12L, ExchangeRateBackfillJobStatus.FAILED).also {
            it.lastErrorMessage = "launcher unavailable"
        }
        `when`(
            backfillJobRepository.findFirstByStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(ACTIVE_STATUSES)
        ).thenReturn(null)
        `when`(backfillJobRepository.saveAndFlush(any(ExchangeRateBackfillJob::class.java))).thenReturn(saved)
        `when`(jobOperator.start(eq(exchangeRateBackfillJob), any(JobParameters::class.java)))
            .thenThrow(IllegalStateException("launcher unavailable"))
        `when`(backfillJobRepository.findByIdAndDeletedAtIsNull(12L)).thenReturn(failed)

        val response = service.runBackfill(1L, backfillRequest())

        assertEquals(ExchangeRateBackfillJobStatus.FAILED, response.status)
        assertEquals("launcher unavailable", response.lastErrorMessage)
        verify(backfillBatchService).markLaunchFailed(eq(12L), anyException())
    }

    @Test
    fun `Batch execution id 누락도 실행 실패로 수렴하고 저장 row fallback을 사용한다`() {
        val saved = backfillJob(12L, ExchangeRateBackfillJobStatus.REQUESTED)
        val execution = mock(JobExecution::class.java)
        `when`(
            backfillJobRepository.findFirstByStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(ACTIVE_STATUSES)
        ).thenReturn(null)
        `when`(backfillJobRepository.saveAndFlush(any(ExchangeRateBackfillJob::class.java))).thenReturn(saved)
        `when`(jobOperator.start(eq(exchangeRateBackfillJob), any(JobParameters::class.java))).thenReturn(execution)

        val response = service.runBackfill(1L, backfillRequest())

        assertEquals(12L, response.id)
        verify(backfillBatchService).markLaunchFailed(eq(12L), anyException())
    }

    @Test
    fun `active unique 충돌이 두 번 반복되고 재사용 job도 없으면 마지막 충돌을 전달한다`() {
        `when`(
            backfillJobRepository.findFirstByStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(ACTIVE_STATUSES)
        ).thenReturn(null)
        `when`(backfillJobRepository.saveAndFlush(any(ExchangeRateBackfillJob::class.java)))
            .thenThrow(DataIntegrityViolationException("active backfill exists"))

        assertFailsWith<DataIntegrityViolationException> {
            service.runBackfill(1L, backfillRequest())
        }
        verify(jobOperator, never()).start(any(Job::class.java), any(JobParameters::class.java))
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

    private fun importRun(
        id: Long,
        rateDate: String,
        status: ExchangeRateImportRunStatus,
    ): ExchangeRateImportRun {
        return ExchangeRateImportRun(
            provider = "KOREA_EXIM",
            rateDate = LocalDate.parse(rateDate),
            status = status,
        ).also { it.id = id }
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
