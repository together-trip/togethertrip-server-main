package com.togethertrip.main.exchange.service

import com.togethertrip.main.exchange.dto.response.ExchangeRateResponse
import com.togethertrip.main.exchange.dto.response.ExchangeRateSearchResponse
import com.togethertrip.main.exchange.repository.ExchangeRateRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class ExchangeRateQueryService(
    private val exchangeRateRepository: ExchangeRateRepository,
    private val clock: Clock,
) {

    fun search(
        baseCurrency: String?,
        targetCurrencies: String?,
        date: LocalDate?,
        from: LocalDate?,
        to: LocalDate?,
    ): ExchangeRateSearchResponse {
        val normalizedBaseCurrency = normalizeBaseCurrency(baseCurrency)
        val normalizedTargetCurrencies = normalizeTargetCurrencies(targetCurrencies)
        validateDateRange(date = date, from = from, to = to)

        if (from != null && to != null) {
            return searchByRange(
                baseCurrency = normalizedBaseCurrency,
                targetCurrencies = normalizedTargetCurrencies,
                from = from,
                to = to,
            )
        }

        return searchByDate(
            baseCurrency = normalizedBaseCurrency,
            targetCurrencies = normalizedTargetCurrencies,
            date = date ?: LocalDate.now(clock),
        )
    }

    private fun searchByDate(
        baseCurrency: String,
        targetCurrencies: List<String>,
        date: LocalDate,
    ): ExchangeRateSearchResponse {
        val rates = exchangeRateRepository
            .findByBaseCurrencyAndTargetCurrencyInAndRateDateAndDeletedAtIsNullOrderByTargetCurrencyAsc(
                baseCurrency = baseCurrency,
                targetCurrencies = targetCurrencies,
                rateDate = date,
            )
            .map(ExchangeRateResponse::from)

        return ExchangeRateSearchResponse(
            baseCurrency = baseCurrency,
            date = date,
            from = null,
            to = null,
            rates = rates,
        )
    }

    private fun searchByRange(
        baseCurrency: String,
        targetCurrencies: List<String>,
        from: LocalDate,
        to: LocalDate,
    ): ExchangeRateSearchResponse {
        val rates = exchangeRateRepository
            .findByBaseCurrencyAndTargetCurrencyInAndRateDateBetweenAndDeletedAtIsNullOrderByTargetCurrencyAscRateDateDesc(
                baseCurrency = baseCurrency,
                targetCurrencies = targetCurrencies,
                from = from,
                to = to,
            )
            .map(ExchangeRateResponse::from)

        return ExchangeRateSearchResponse(
            baseCurrency = baseCurrency,
            date = null,
            from = from,
            to = to,
            rates = rates,
        )
    }

    private fun normalizeBaseCurrency(baseCurrency: String?): String {
        val normalized = baseCurrency?.trim()?.uppercase().orEmpty().ifBlank { BASE_CURRENCY }
        require(normalized == BASE_CURRENCY) {
            "baseCurrency는 KRW만 지원합니다."
        }
        return normalized
    }

    private fun normalizeTargetCurrencies(targetCurrencies: String?): List<String> {
        val currencies = targetCurrencies
            ?.split(",")
            ?.map { currency -> currency.trim().uppercase() }
            ?.filter { currency -> currency.isNotBlank() }
            ?.distinct()
            ?.ifEmpty { DEFAULT_TARGET_CURRENCIES }
            ?: DEFAULT_TARGET_CURRENCIES

        require(currencies.all { currency -> CURRENCY_PATTERN.matches(currency) }) {
            "targetCurrencies는 ISO 4217 3자리 통화 코드여야 합니다."
        }
        return currencies
    }

    private fun validateDateRange(
        date: LocalDate?,
        from: LocalDate?,
        to: LocalDate?,
    ) {
        require(date == null || (from == null && to == null)) {
            "date와 from/to는 동시에 사용할 수 없습니다."
        }
        require((from == null) == (to == null)) {
            "from과 to는 함께 입력해야 합니다."
        }

        if (from != null && to != null) {
            require(!from.isAfter(to)) {
                "from은 to보다 이후일 수 없습니다."
            }
            require(ChronoUnit.DAYS.between(from, to) <= MAX_RANGE_DAYS) {
                "조회 기간은 최대 ${MAX_RANGE_DAYS + 1}일까지 가능합니다."
            }
        }
    }

    companion object {
        private const val BASE_CURRENCY = "KRW"
        private const val MAX_RANGE_DAYS = 89L
        private val CURRENCY_PATTERN = Regex("^[A-Z]{3}$")
        private val DEFAULT_TARGET_CURRENCIES = listOf(
            "USD",
            "JPY",
            "EUR",
            "CNY",
            "TWD",
            "HKD",
            "VND",
            "THB",
            "SGD",
        )
    }
}
