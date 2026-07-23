package com.togethertrip.main.exchange.service

import com.togethertrip.main.exchange.domain.ExchangeRate
import com.togethertrip.main.exchange.repository.ExchangeRateRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExchangeRateQueryServiceTest {

    private lateinit var exchangeRateRepository: ExchangeRateRepository
    private lateinit var service: ExchangeRateQueryService

    @BeforeEach
    fun setUp() {
        exchangeRateRepository = mock(ExchangeRateRepository::class.java)
        service = ExchangeRateQueryService(
            exchangeRateRepository = exchangeRateRepository,
            clock = Clock.fixed(
                Instant.parse("2026-06-17T00:30:00Z"),
                ZoneId.of("Asia/Seoul"),
            ),
        )
    }

    @Test
    fun `date가 있으면 해당 날짜의 환율을 조회한다`() {
        `when`(
            exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyInAndRateDateAndDeletedAtIsNullOrderByTargetCurrencyAsc(
                baseCurrency = "KRW",
                targetCurrencies = listOf("USD", "JPY"),
                rateDate = LocalDate.of(2026, 6, 17),
            )
        ).thenReturn(
            listOf(
                exchangeRate("JPY", "9.500000", LocalDate.of(2026, 6, 17)),
                exchangeRate("USD", "1380.120000", LocalDate.of(2026, 6, 17)),
            )
        )

        val response = service.search(
            baseCurrency = "krw",
            targetCurrencies = "usd, jpy",
            date = LocalDate.of(2026, 6, 17),
            from = null,
            to = null,
        )

        assertEquals("KRW", response.baseCurrency)
        assertEquals(LocalDate.of(2026, 6, 17), response.date)
        assertEquals(null, response.from)
        assertEquals(null, response.to)
        assertEquals(listOf("JPY", "USD"), response.rates.map { it.targetCurrency })
        assertEquals(BigDecimal("9.500000"), response.rates.first().rate)
    }

    @Test
    fun `date가 없고 기간도 없으면 오늘 날짜로 조회한다`() {
        `when`(
            exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyInAndRateDateAndDeletedAtIsNullOrderByTargetCurrencyAsc(
                baseCurrency = "KRW",
                targetCurrencies = DEFAULT_TARGET_CURRENCIES,
                rateDate = LocalDate.of(2026, 6, 17),
            )
        ).thenReturn(listOf(exchangeRate("USD", "1380.120000", LocalDate.of(2026, 6, 17))))

        val response = service.search(
            baseCurrency = null,
            targetCurrencies = null,
            date = null,
            from = null,
            to = null,
        )

        assertEquals(LocalDate.of(2026, 6, 17), response.date)
        assertEquals(listOf("USD"), response.rates.map { it.targetCurrency })
    }

    @Test
    fun `from과 to가 있으면 기간 환율을 조회한다`() {
        `when`(
            exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyInAndRateDateBetweenAndDeletedAtIsNullOrderByTargetCurrencyAscRateDateDesc(
                baseCurrency = "KRW",
                targetCurrencies = listOf("USD"),
                from = LocalDate.of(2026, 6, 1),
                to = LocalDate.of(2026, 6, 3),
            )
        ).thenReturn(
            listOf(
                exchangeRate("USD", "1380.120000", LocalDate.of(2026, 6, 3)),
                exchangeRate("USD", "1375.000000", LocalDate.of(2026, 6, 2)),
            )
        )

        val response = service.search(
            baseCurrency = "KRW",
            targetCurrencies = "USD",
            date = null,
            from = LocalDate.of(2026, 6, 1),
            to = LocalDate.of(2026, 6, 3),
        )

        assertEquals(null, response.date)
        assertEquals(LocalDate.of(2026, 6, 1), response.from)
        assertEquals(LocalDate.of(2026, 6, 3), response.to)
        assertEquals(
            listOf(LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 2)),
            response.rates.map { it.rateDate },
        )
    }

    @Test
    fun `date와 기간 조건을 동시에 입력하면 실패한다`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.search(
                baseCurrency = "KRW",
                targetCurrencies = "USD",
                date = LocalDate.of(2026, 6, 17),
                from = LocalDate.of(2026, 6, 1),
                to = LocalDate.of(2026, 6, 3),
            )
        }

        assertEquals("date와 from/to는 동시에 사용할 수 없습니다.", exception.message)
    }

    @Test
    fun `from과 to 중 하나만 입력하면 실패한다`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.search(
                baseCurrency = "KRW",
                targetCurrencies = "USD",
                date = null,
                from = LocalDate.of(2026, 6, 1),
                to = null,
            )
        }

        assertEquals("from과 to는 함께 입력해야 합니다.", exception.message)
    }

    @Test
    fun `지원하지 않는 기준 통화이면 실패한다`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.search(
                baseCurrency = "USD",
                targetCurrencies = "JPY",
                date = LocalDate.of(2026, 6, 17),
                from = null,
                to = null,
            )
        }

        assertEquals("baseCurrency는 KRW만 지원합니다.", exception.message)
    }

    @Test
    fun `통화 코드가 ISO 4217 형식이 아니면 실패한다`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.search(
                baseCurrency = "KRW",
                targetCurrencies = "USDT",
                date = LocalDate.of(2026, 6, 17),
                from = null,
                to = null,
            )
        }

        assertEquals("targetCurrencies는 ISO 4217 3자리 통화 코드여야 합니다.", exception.message)
    }

    @Test
    fun `조회 기간이 90일을 초과하면 실패한다`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.search(
                baseCurrency = "KRW",
                targetCurrencies = "USD",
                date = null,
                from = LocalDate.of(2026, 1, 1),
                to = LocalDate.of(2026, 4, 1),
            )
        }

        assertEquals("조회 기간은 최대 90일까지 가능합니다.", exception.message)
    }

    private fun exchangeRate(
        targetCurrency: String,
        rate: String,
        rateDate: LocalDate,
    ): ExchangeRate {
        return ExchangeRate(
            baseCurrency = "KRW",
            targetCurrency = targetCurrency,
            rate = BigDecimal(rate),
            rateDate = rateDate,
            source = "KOREA_EXIM",
        )
    }

    companion object {
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
