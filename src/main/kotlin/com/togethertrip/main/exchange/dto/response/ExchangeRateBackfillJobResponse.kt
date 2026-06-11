package com.togethertrip.main.exchange.dto.response

import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJob
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJobStatus
import java.time.Instant
import java.time.LocalDate

data class ExchangeRateBackfillJobResponse(
    val id: Long,
    val requestedBy: Long,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val pauseBetweenRequestsMillis: Long,
    val batchJobExecutionId: Long?,
    val status: ExchangeRateBackfillJobStatus,
    val totalRequestedDays: Long,
    val processedDays: Int,
    val successCount: Int,
    val failedCount: Int,
    val noDataCount: Int,
    val nonBusinessDayCount: Int,
    val lastErrorMessage: String?,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun from(job: ExchangeRateBackfillJob): ExchangeRateBackfillJobResponse {
            return ExchangeRateBackfillJobResponse(
                id = job.id,
                requestedBy = job.requestedBy,
                fromDate = job.fromDate,
                toDate = job.toDate,
                pauseBetweenRequestsMillis = job.pauseBetweenRequestsMillis,
                batchJobExecutionId = job.batchJobExecutionId,
                status = job.status,
                totalRequestedDays = job.totalRequestedDays,
                processedDays = job.processedDays,
                successCount = job.successCount,
                failedCount = job.failedCount,
                noDataCount = job.noDataCount,
                nonBusinessDayCount = job.nonBusinessDayCount,
                lastErrorMessage = job.lastErrorMessage,
                startedAt = job.startedAt,
                finishedAt = job.finishedAt,
                createdAt = job.createdAt,
            )
        }
    }
}
