package com.togethertrip.main.exchange.service

import com.togethertrip.main.exchange.client.KoreaEximExchangeRateResultCode
import java.time.LocalDate

sealed interface ExchangeRateImportResult {
    val rateDate: LocalDate

    data class Imported(
        override val rateDate: LocalDate,
        val rowCount: Int,
        val upsertCount: Int,
    ) : ExchangeRateImportResult

    data class NoData(
        override val rateDate: LocalDate,
    ) : ExchangeRateImportResult

    data class Failed(
        override val rateDate: LocalDate,
        val resultCode: KoreaEximExchangeRateResultCode,
    ) : ExchangeRateImportResult

    data class Error(
        override val rateDate: LocalDate,
        val message: String,
    ) : ExchangeRateImportResult
}
