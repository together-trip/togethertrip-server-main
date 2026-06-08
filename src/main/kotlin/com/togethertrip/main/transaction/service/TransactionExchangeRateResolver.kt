package com.togethertrip.main.transaction.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.transaction.domain.TransactionExchangeRatePreview
import com.togethertrip.main.transaction.exception.TransactionErrorCode
import com.togethertrip.main.trip.repository.ExchangeRateRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@Component
class TransactionExchangeRateResolver(
    private val exchangeRateRepository: ExchangeRateRepository,
    private val clock: Clock,
) {

    fun resolve(
        currency: String,
        spendingDate: LocalDate?,
    ): TransactionExchangeRatePreview {
        val normalizedCurrency = currency.trim().uppercase()
        val rateBaseDate = spendingDate ?: LocalDate.now(clock)

        if (normalizedCurrency == BASE_CURRENCY) {
            return TransactionExchangeRatePreview(
                currency = normalizedCurrency,
                baseCurrency = BASE_CURRENCY,
                rate = BASE_CURRENCY_EXCHANGE_RATE,
                rateDate = rateBaseDate,
                source = SOURCE_BASE_CURRENCY,
            )
        }

        val exchangeRate = exchangeRateRepository
            .findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = BASE_CURRENCY,
                targetCurrency = normalizedCurrency,
                rateDate = rateBaseDate,
            ) ?: throw BusinessException(TransactionErrorCode.EXCHANGE_RATE_NOT_READY)

        if (exchangeRate.rate <= BigDecimal.ZERO) {
            throw BusinessException(TransactionErrorCode.EXCHANGE_RATE_NOT_READY)
        }

        return TransactionExchangeRatePreview(
            currency = normalizedCurrency,
            baseCurrency = BASE_CURRENCY,
            rate = exchangeRate.rate,
            rateDate = exchangeRate.rateDate,
            source = exchangeRate.source,
        )
    }

    companion object {
        private const val BASE_CURRENCY = "KRW"
        private const val SOURCE_BASE_CURRENCY = "BASE_CURRENCY"
        private val BASE_CURRENCY_EXCHANGE_RATE = BigDecimal("1.000000")
    }
}
