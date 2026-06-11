package com.togethertrip.main.exchange.service.normalizer

import com.togethertrip.main.exchange.client.KoreaEximExchangeRateException
import com.togethertrip.main.exchange.client.KoreaEximExchangeRateResponse
import com.togethertrip.main.exchange.domain.ExchangeRateImportRow
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Component
class KoreaEximExchangeRateNormalizer {

    fun normalize(
        response: KoreaEximExchangeRateResponse,
        rateDate: LocalDate,
    ): ExchangeRateImportRow {
        val rawCurrencyUnit = response.currencyUnit?.trim().orEmpty()
        val dealBaseRate = response.dealBaseRate?.trim().orEmpty()

        if (rawCurrencyUnit.isBlank() || dealBaseRate.isBlank()) {
            throw KoreaEximExchangeRateException("환율 응답에 통화 또는 환율 값이 없습니다.")
        }

        val unit = parseCurrencyUnit(rawCurrencyUnit)
        val rate = parseRate(dealBaseRate)
            .divide(unit.multiplier, RATE_SCALE, RoundingMode.HALF_UP)

        if (rate <= BigDecimal.ZERO) {
            throw KoreaEximExchangeRateException("환율 값은 0보다 커야 합니다.")
        }

        return ExchangeRateImportRow(
            baseCurrency = BASE_CURRENCY,
            targetCurrency = unit.currency,
            rate = rate,
            rateDate = rateDate,
            source = SOURCE,
        )
    }

    private fun parseCurrencyUnit(rawCurrencyUnit: String): CurrencyUnit {
        val match = UNIT_PATTERN.matchEntire(rawCurrencyUnit)
            ?: throw KoreaEximExchangeRateException("지원하지 않는 통화 단위입니다: $rawCurrencyUnit")

        val currency = match.groupValues[1].uppercase()
        val multiplier = match.groupValues
            .getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.toBigDecimal()
            ?: BigDecimal.ONE

        return CurrencyUnit(currency, multiplier)
    }

    private fun parseRate(rawRate: String): BigDecimal {
        return try {
            rawRate.replace(",", "").toBigDecimal()
        } catch (exception: NumberFormatException) {
            throw KoreaEximExchangeRateException("환율 값을 숫자로 변환할 수 없습니다.")
        }
    }

    private data class CurrencyUnit(
        val currency: String,
        val multiplier: BigDecimal,
    )

    companion object {
        private const val BASE_CURRENCY = "KRW"
        private const val SOURCE = "KOREA_EXIM"
        private const val RATE_SCALE = 6
        private val UNIT_PATTERN = Regex("""^([A-Za-z]{3})(?:\((\d+)\))?$""")
    }
}
