package com.togethertrip.main.exchange.service

import com.togethertrip.main.exchange.domain.ExchangeRateImportRun
import com.togethertrip.main.exchange.domain.ExchangeRateImportRunStatus
import com.togethertrip.main.exchange.repository.ExchangeRateImportRunRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service
class ExchangeRateImportRunService(
    private val importRunRepository: ExchangeRateImportRunRepository,
) {

    fun hasSucceeded(rateDate: LocalDate): Boolean {
        return importRunRepository.existsByProviderAndRateDateAndStatusAndDeletedAtIsNull(
            provider = PROVIDER,
            rateDate = rateDate,
            status = ExchangeRateImportRunStatus.SUCCESS,
        )
    }

    fun hasCompleted(rateDate: LocalDate): Boolean {
        return importRunRepository.existsByProviderAndRateDateAndStatusInAndDeletedAtIsNull(
            provider = PROVIDER,
            rateDate = rateDate,
            statuses = COMPLETED_STATUSES,
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun start(rateDate: LocalDate) {
        val now = Instant.now()
        val run = importRunRepository.findActiveForUpdate(PROVIDER, rateDate)
            ?: ExchangeRateImportRun(
                provider = PROVIDER,
                rateDate = rateDate,
            )

        run.status = ExchangeRateImportRunStatus.RUNNING
        run.attemptCount += 1
        run.startedAt = now
        run.finishedAt = null
        run.lastResultCode = null
        run.lastErrorMessage = null
        run.updatedAt = now

        importRunRepository.save(run)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun finish(result: ExchangeRateImportResult) {
        val now = Instant.now()
        val run = importRunRepository.findActiveForUpdate(PROVIDER, result.rateDate)
            ?: ExchangeRateImportRun(
                provider = PROVIDER,
                rateDate = result.rateDate,
            )

        when (result) {
            is ExchangeRateImportResult.Imported -> {
                run.status = ExchangeRateImportRunStatus.SUCCESS
                run.rowCount = result.rowCount
                run.upsertCount = result.upsertCount
                run.lastResultCode = null
                run.lastErrorMessage = null
            }
            is ExchangeRateImportResult.NoData -> {
                run.status = ExchangeRateImportRunStatus.NO_DATA
                run.rowCount = 0
                run.upsertCount = 0
                run.lastResultCode = "NO_DATA"
                run.lastErrorMessage = null
            }
            is ExchangeRateImportResult.NonBusinessDay -> {
                run.status = ExchangeRateImportRunStatus.NON_BUSINESS_DAY
                run.rowCount = 0
                run.upsertCount = 0
                run.lastResultCode = "NON_BUSINESS_DAY"
                run.lastErrorMessage = null
            }
            is ExchangeRateImportResult.Failed -> {
                run.status = ExchangeRateImportRunStatus.FAILED
                run.rowCount = 0
                run.upsertCount = 0
                run.lastResultCode = result.resultCode.code.toString()
                run.lastErrorMessage = result.resultCode.description.take(MAX_ERROR_MESSAGE_LENGTH)
            }
            is ExchangeRateImportResult.Error -> {
                run.status = ExchangeRateImportRunStatus.FAILED
                run.rowCount = 0
                run.upsertCount = 0
                run.lastResultCode = "EXCEPTION"
                run.lastErrorMessage = result.message.take(MAX_ERROR_MESSAGE_LENGTH)
            }
        }

        run.finishedAt = now
        run.updatedAt = now
        importRunRepository.save(run)
    }

    companion object {
        private const val PROVIDER = "KOREA_EXIM"
        private const val MAX_ERROR_MESSAGE_LENGTH = 500
        private val COMPLETED_STATUSES = listOf(
            ExchangeRateImportRunStatus.SUCCESS,
            ExchangeRateImportRunStatus.NON_BUSINESS_DAY,
        )
    }
}
