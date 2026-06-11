package com.togethertrip.main.exchange.dto.response

import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJob
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJobStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ExchangeRateBackfillJobResponse(
    val id: Long,
    val requestedBy: Long,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val pauseBetweenRequestsMillis: Long,
    val batchJobExecutionId: Long?,
    val status: ExchangeRateBackfillJobStatus,
    val requestedDays: Long,
    val targetDays: Long,
    val processedDays: Int,
    val progressPercent: BigDecimal,
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
                requestedDays = calculateRequestedDays(job),
                targetDays = job.totalRequestedDays,
                processedDays = job.processedDays,
                progressPercent = calculateProgressPercent(job),
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

        private fun calculateRequestedDays(job: ExchangeRateBackfillJob): Long {
            return ChronoUnit.DAYS.between(job.fromDate, job.toDate) + 1
        }

        private fun calculateProgressPercent(job: ExchangeRateBackfillJob): BigDecimal {
            if (job.totalRequestedDays <= 0) {
                return BigDecimal.ZERO.setScale(PROGRESS_SCALE)
            }

            val boundedProcessedDays = job.processedDays.toLong()
                .coerceAtMost(job.totalRequestedDays)
                .coerceAtLeast(0)

            return BigDecimal(boundedProcessedDays)
                .multiply(BigDecimal(100))
                .divide(BigDecimal(job.totalRequestedDays), PROGRESS_SCALE, RoundingMode.HALF_UP)
        }

        private const val PROGRESS_SCALE = 2
    }
}
