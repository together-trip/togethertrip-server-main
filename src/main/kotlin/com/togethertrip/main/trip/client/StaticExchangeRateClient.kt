package com.togethertrip.main.trip.client

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.trip.exception.TripErrorCode
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class StaticExchangeRateClient : ExchangeRateClient {

    override fun fetchRates(
        baseCurrency: String,
        targetCurrencies: Set<String>,
        rateDate: LocalDate,
    ): List<ExchangeRateQuote> {
        val normalizedBaseCurrency = baseCurrency.trim().uppercase()

        return targetCurrencies
            .map { it.trim().uppercase() }
            .filterNot { it == normalizedBaseCurrency }
            .map { targetCurrency ->
                val rate = rates[normalizedBaseCurrency to targetCurrency]
                    ?: throw BusinessException(TripErrorCode.EXCHANGE_RATE_FETCH_FAILED)

                ExchangeRateQuote(
                    baseCurrency = normalizedBaseCurrency,
                    targetCurrency = targetCurrency,
                    rate = rate,
                    rateDate = rateDate,
                    source = SOURCE,
                )
            }
    }

    companion object {
        private const val SOURCE = "STATIC_FIXTURE"

        private val rates = mapOf(
            ("KRW" to "JPY") to BigDecimal("9.150000"),
            ("KRW" to "VND") to BigDecimal("0.054000"),
            ("KRW" to "THB") to BigDecimal("38.170000"),
            ("KRW" to "USD") to BigDecimal("1350.000000"),
            ("KRW" to "EUR") to BigDecimal("1450.000000"),
            ("KRW" to "CNY") to BigDecimal("186.000000"),
            ("KRW" to "TWD") to BigDecimal("42.000000"),
            ("KRW" to "HKD") to BigDecimal("173.000000"),
            ("KRW" to "SGD") to BigDecimal("1000.000000"),
            ("KRW" to "MYR") to BigDecimal("285.000000"),
            ("KRW" to "PHP") to BigDecimal("23.000000"),
            ("KRW" to "IDR") to BigDecimal("0.083000"),
        )
    }
}
