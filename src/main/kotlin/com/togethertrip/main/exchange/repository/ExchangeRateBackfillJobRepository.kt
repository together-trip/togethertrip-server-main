package com.togethertrip.main.exchange.repository

import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJob
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJobStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface ExchangeRateBackfillJobRepository : JpaRepository<ExchangeRateBackfillJob, Long> {

    fun findByDeletedAtIsNullOrderByCreatedAtDesc(pageable: Pageable): List<ExchangeRateBackfillJob>

    fun findByIdAndDeletedAtIsNull(id: Long): ExchangeRateBackfillJob?

    fun findFirstByStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
        statuses: Collection<ExchangeRateBackfillJobStatus>,
    ): ExchangeRateBackfillJob?

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ExchangeRateBackfillJob job
        SET job.batchJobExecutionId = :batchJobExecutionId,
            job.updatedAt = :updatedAt
        WHERE job.id = :id
          AND job.deletedAt IS NULL
        """
    )
    fun updateBatchJobExecutionId(
        @Param("id") id: Long,
        @Param("batchJobExecutionId") batchJobExecutionId: Long,
        @Param("updatedAt") updatedAt: Instant,
    ): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ExchangeRateBackfillJob job
        SET job.status = :status,
            job.totalRequestedDays = :totalRequestedDays,
            job.startedAt = :startedAt,
            job.finishedAt = NULL,
            job.lastErrorMessage = NULL,
            job.updatedAt = :updatedAt
        WHERE job.id = :id
          AND job.deletedAt IS NULL
        """
    )
    fun markRunning(
        @Param("id") id: Long,
        @Param("status") status: ExchangeRateBackfillJobStatus,
        @Param("totalRequestedDays") totalRequestedDays: Long,
        @Param("startedAt") startedAt: Instant,
        @Param("updatedAt") updatedAt: Instant,
    ): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ExchangeRateBackfillJob job
        SET job.processedDays = job.processedDays + 1,
            job.successCount = job.successCount + :successIncrement,
            job.failedCount = job.failedCount + :failedIncrement,
            job.noDataCount = job.noDataCount + :noDataIncrement,
            job.nonBusinessDayCount = job.nonBusinessDayCount + :nonBusinessDayIncrement,
            job.updatedAt = :updatedAt
        WHERE job.id = :id
          AND job.deletedAt IS NULL
        """
    )
    fun incrementProgress(
        @Param("id") id: Long,
        @Param("successIncrement") successIncrement: Int,
        @Param("failedIncrement") failedIncrement: Int,
        @Param("noDataIncrement") noDataIncrement: Int,
        @Param("nonBusinessDayIncrement") nonBusinessDayIncrement: Int,
        @Param("updatedAt") updatedAt: Instant,
    ): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ExchangeRateBackfillJob job
        SET job.status = :status,
            job.finishedAt = :finishedAt,
            job.updatedAt = :updatedAt
        WHERE job.id = :id
          AND job.deletedAt IS NULL
        """
    )
    fun markFinished(
        @Param("id") id: Long,
        @Param("status") status: ExchangeRateBackfillJobStatus,
        @Param("finishedAt") finishedAt: Instant,
        @Param("updatedAt") updatedAt: Instant,
    ): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ExchangeRateBackfillJob job
        SET job.status = :status,
            job.lastErrorMessage = :lastErrorMessage,
            job.finishedAt = :finishedAt,
            job.updatedAt = :updatedAt
        WHERE job.id = :id
          AND job.deletedAt IS NULL
        """
    )
    fun markFailed(
        @Param("id") id: Long,
        @Param("status") status: ExchangeRateBackfillJobStatus,
        @Param("lastErrorMessage") lastErrorMessage: String,
        @Param("finishedAt") finishedAt: Instant,
        @Param("updatedAt") updatedAt: Instant,
    ): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ExchangeRateBackfillJob job
        SET job.status = :status,
            job.lastErrorMessage = :lastErrorMessage,
            job.finishedAt = :finishedAt,
            job.updatedAt = :updatedAt
        WHERE job.id = :id
          AND job.deletedAt IS NULL
          AND job.status NOT IN :terminalStatuses
        """
    )
    fun markFailedIfNotTerminal(
        @Param("id") id: Long,
        @Param("status") status: ExchangeRateBackfillJobStatus,
        @Param("lastErrorMessage") lastErrorMessage: String,
        @Param("finishedAt") finishedAt: Instant,
        @Param("updatedAt") updatedAt: Instant,
        @Param("terminalStatuses") terminalStatuses: Collection<ExchangeRateBackfillJobStatus>,
    ): Int
}
