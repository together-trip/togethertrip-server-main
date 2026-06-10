package com.togethertrip.main.exchange.repository

import com.togethertrip.main.exchange.domain.ExchangeRateImportRow
import org.mockito.ArgumentCaptor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExchangeRateUpsertRepositoryTest {

    private lateinit var jdbcTemplate: NamedParameterJdbcTemplate
    private lateinit var repository: ExchangeRateUpsertRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate::class.java)
        repository = ExchangeRateUpsertRepository(jdbcTemplate)
    }

    @Test
    fun `빈 row 목록은 DB를 호출하지 않고 0을 반환한다`() {
        val result = repository.upsertAll(emptyList())

        assertEquals(0, result)
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource::class.java))
    }

    @Test
    fun `여러 row를 하나의 bulk upsert query로 저장한다`() {
        val rows = listOf(
            ExchangeRateImportRow(
                baseCurrency = "KRW",
                targetCurrency = "USD",
                rate = BigDecimal("1350.000000"),
                rateDate = LocalDate.parse("2026-06-10"),
                source = "KOREA_EXIM",
            ),
            ExchangeRateImportRow(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rate = BigDecimal("9.300000"),
                rateDate = LocalDate.parse("2026-06-10"),
                source = "KOREA_EXIM",
            ),
        )

        `when`(jdbcTemplate.update(anyString(), any(MapSqlParameterSource::class.java))).thenReturn(2)

        val result = repository.upsertAll(rows)

        assertEquals(2, result)
        val sqlCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(MapSqlParameterSource::class.java))

        val sql = sqlCaptor.value
        assertTrue(sql.contains("INSERT INTO exchange_rates"))
        assertTrue(sql.contains(":baseCurrency0"))
        assertTrue(sql.contains(":baseCurrency1"))
        assertTrue(sql.contains("ON CONFLICT"))
    }
}
