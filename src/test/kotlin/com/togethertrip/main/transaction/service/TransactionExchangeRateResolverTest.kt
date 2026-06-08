package com.togethertrip.main.transaction.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.transaction.exception.TransactionErrorCode
import com.togethertrip.main.trip.domain.ExchangeRate
import com.togethertrip.main.trip.repository.ExchangeRateRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

class TransactionExchangeRateResolverTest {

    private lateinit var exchangeRateRepository: ExchangeRateRepository
    private lateinit var resolver: TransactionExchangeRateResolver

    @BeforeEach
    fun setUp() {
        exchangeRateRepository = mock(ExchangeRateRepository::class.java)
        resolver = TransactionExchangeRateResolver(
            exchangeRateRepository = exchangeRateRepository,
            clock = Clock.fixed(
                Instant.parse("2026-07-02T00:30:00Z"),
                ZoneId.of("Asia/Seoul"),
            ),
        )
    }

    @Test
    fun `KRW는 환율 DB를 조회하지 않고 기준 통화 환율을 반환한다`() {
        val preview = resolver.resolve(
            currency = "krw",
            spendingDate = null,
        )

        assertEquals("KRW", preview.currency)
        assertEquals("KRW", preview.baseCurrency)
        assertEquals(BigDecimal("1.000000"), preview.rate)
        assertEquals(LocalDate.of(2026, 7, 2), preview.rateDate)
        assertEquals("BASE_CURRENCY", preview.source)
        verifyNoInteractions(exchangeRateRepository)
    }

    @Test
    fun `외화는 소비일 이하 최신 환율을 반환한다`() {
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 8, 10),
            )
        ).thenReturn(
            createExchangeRate(
                rate = BigDecimal("9.300000"),
                rateDate = LocalDate.of(2026, 7, 2),
            )
        )

        val preview = resolver.resolve(
            currency = "jpy",
            spendingDate = LocalDate.of(2026, 8, 10),
        )

        assertEquals("JPY", preview.currency)
        assertEquals("KRW", preview.baseCurrency)
        assertEquals(BigDecimal("9.300000"), preview.rate)
        assertEquals(LocalDate.of(2026, 7, 2), preview.rateDate)
        assertEquals("TEST", preview.source)
    }

    @Test
    fun `소비일이 없으면 현시점 기준 최신 환율을 반환한다`() {
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 7, 2),
            )
        ).thenReturn(createExchangeRate())

        val preview = resolver.resolve(
            currency = "JPY",
            spendingDate = null,
        )

        assertEquals(BigDecimal("9.150000"), preview.rate)
        assertEquals(LocalDate.of(2026, 7, 2), preview.rateDate)
    }

    @Test
    fun `기준일 이하 환율이 없으면 실패한다`() {
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 7, 2),
            )
        ).thenReturn(null)

        val exception = assertBusinessException {
            resolver.resolve(
                currency = "JPY",
                spendingDate = null,
            )
        }

        assertEquals(TransactionErrorCode.EXCHANGE_RATE_NOT_READY, exception.errorCode)
    }

    @Test
    fun `환율이 0 이하이면 실패한다`() {
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 7, 2),
            )
        ).thenReturn(createExchangeRate(rate = BigDecimal.ZERO))

        val exception = assertBusinessException {
            resolver.resolve(
                currency = "JPY",
                spendingDate = null,
            )
        }

        assertEquals(TransactionErrorCode.EXCHANGE_RATE_NOT_READY, exception.errorCode)
    }

    private fun createExchangeRate(
        rate: BigDecimal = BigDecimal("9.150000"),
        rateDate: LocalDate = LocalDate.of(2026, 7, 2),
    ): ExchangeRate {
        return ExchangeRate(
            baseCurrency = "KRW",
            targetCurrency = "JPY",
            rate = rate,
            rateDate = rateDate,
            source = "TEST",
        )
    }

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try {
            block()
            throw AssertionError("BusinessException이 발생해야 합니다.")
        } catch (exception: BusinessException) {
            exception
        }
    }
}
