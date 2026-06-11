package com.togethertrip.main.exchange.service.normalizer

import com.togethertrip.main.exchange.client.KoreaEximExchangeRateException
import com.togethertrip.main.exchange.client.KoreaEximExchangeRateResponse
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KoreaEximExchangeRateNormalizerTest {

    private val normalizer = KoreaEximExchangeRateNormalizer()

    @Test
    fun `comma가 포함된 환율을 BigDecimal로 변환한다`() {
        val row = normalizer.normalize(
            response = KoreaEximExchangeRateResponse(
                currencyUnit = "USD",
                dealBaseRate = "1,350.25",
            ),
            rateDate = LocalDate.parse("2026-06-10"),
        )

        assertEquals("KRW", row.baseCurrency)
        assertEquals("USD", row.targetCurrency)
        assertEquals(BigDecimal("1350.250000"), row.rate)
        assertEquals(LocalDate.parse("2026-06-10"), row.rateDate)
        assertEquals("KOREA_EXIM", row.source)
    }

    @Test
    fun `100단위 통화는 1단위당 KRW 환율로 정규화한다`() {
        val row = normalizer.normalize(
            response = KoreaEximExchangeRateResponse(
                currencyUnit = "JPY(100)",
                dealBaseRate = "915.00",
            ),
            rateDate = LocalDate.parse("2026-06-10"),
        )

        assertEquals("JPY", row.targetCurrency)
        assertEquals(BigDecimal("9.150000"), row.rate)
    }

    @Test
    fun `0 이하 환율은 실패한다`() {
        assertFailsWith<KoreaEximExchangeRateException> {
            normalizer.normalize(
                response = KoreaEximExchangeRateResponse(
                    currencyUnit = "USD",
                    dealBaseRate = "0",
                ),
                rateDate = LocalDate.parse("2026-06-10"),
            )
        }
    }

    @Test
    fun `숫자가 아닌 환율은 실패한다`() {
        assertFailsWith<KoreaEximExchangeRateException> {
            normalizer.normalize(
                response = KoreaEximExchangeRateResponse(
                    currencyUnit = "USD",
                    dealBaseRate = "not-number",
                ),
                rateDate = LocalDate.parse("2026-06-10"),
            )
        }
    }
}
