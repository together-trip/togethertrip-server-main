package com.togethertrip.main.exchange.dto.response

import com.togethertrip.main.exchange.domain.ExchangeRateImportRun
import com.togethertrip.main.exchange.domain.ExchangeRateImportRunStatus
import java.time.Instant
import java.time.LocalDate

data class ExchangeRateImportRunResponse(
    val id: Long,
    val provider: String,
    val rateDate: LocalDate,
    val status: ExchangeRateImportRunStatus,
    val attemptCount: Int,
    val rowCount: Int,
    val upsertCount: Int,
    val lastResultCode: String?,
    val lastErrorMessage: String?,
    val startedAt: Instant?,
    val finishedAt: Instant?,
) {
    companion object {
        fun from(run: ExchangeRateImportRun): ExchangeRateImportRunResponse {
            return ExchangeRateImportRunResponse(
                id = run.id,
                provider = run.provider,
                rateDate = run.rateDate,
                status = run.status,
                attemptCount = run.attemptCount,
                rowCount = run.rowCount,
                upsertCount = run.upsertCount,
                lastResultCode = run.lastResultCode,
                lastErrorMessage = run.lastErrorMessage,
                startedAt = run.startedAt,
                finishedAt = run.finishedAt,
            )
        }
    }
}
