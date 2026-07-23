package com.togethertrip.main.exchange.repository

import com.togethertrip.main.exchange.domain.ExchangeRateImportRow
import com.togethertrip.main.global.config.MainIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@MainIntegrationTest
@Transactional
class ExchangeRateUpsertRepositoryIntegrationTest @Autowired constructor(
    private val repository: ExchangeRateUpsertRepository,
    private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `여러 환율 row를 실제 PostgreSQL에 한 번에 저장한다`() {
        val rows = listOf(
            row(targetCurrency = "USD", rate = "1350.000000"),
            row(targetCurrency = "JPY", rate = "9.300000"),
        )

        val affectedRows = repository.upsertAll(rows)

        assertEquals(2, affectedRows)
        assertEquals(
            2,
            jdbcTemplate.queryForObject(
                "select count(*) from exchange_rates where rate_date = ? and deleted_at is null",
                Int::class.java,
                RATE_DATE,
            ),
        )
    }

    @Test
    fun `동일한 환율을 다시 수집하면 PostgreSQL update를 수행하지 않는다`() {
        val row = row(targetCurrency = "USD", rate = "1350.000000")
        repository.upsertAll(listOf(row))

        val affectedRows = repository.upsertAll(listOf(row))

        assertEquals(0, affectedRows)
        assertEquals(1, countRows(targetCurrency = "USD"))
    }

    @Test
    fun `환율이나 출처가 바뀌면 기존 active row를 갱신한다`() {
        repository.upsertAll(
            listOf(row(targetCurrency = "USD", rate = "1350.000000", source = "OLD_SOURCE"))
        )
        val originalId = findId(targetCurrency = "USD")

        val affectedRows = repository.upsertAll(
            listOf(row(targetCurrency = "USD", rate = "1400.000000", source = "NEW_SOURCE"))
        )

        val stored = jdbcTemplate.queryForMap(
            """
            select id, rate, source
            from exchange_rates
            where base_currency = ?
              and target_currency = ?
              and rate_date = ?
              and deleted_at is null
            """.trimIndent(),
            BASE_CURRENCY,
            "USD",
            RATE_DATE,
        )
        assertEquals(1, affectedRows)
        assertEquals(originalId, (stored["id"] as Number).toLong())
        assertEquals(0, (stored["rate"] as BigDecimal).compareTo(BigDecimal("1400.000000")))
        assertEquals("NEW_SOURCE", stored["source"])
    }

    @Test
    fun `soft delete된 환율과 같은 key는 새로운 active row로 저장한다`() {
        repository.upsertAll(listOf(row(targetCurrency = "USD", rate = "1350.000000")))
        val deletedId = findId(targetCurrency = "USD")
        jdbcTemplate.update(
            "update exchange_rates set deleted_at = now() where id = ?",
            deletedId,
        )

        val affectedRows = repository.upsertAll(
            listOf(row(targetCurrency = "USD", rate = "1400.000000"))
        )

        val activeId = findId(targetCurrency = "USD")
        assertEquals(1, affectedRows)
        assertEquals(2, countRows(targetCurrency = "USD", activeOnly = false))
        assertEquals(1, countRows(targetCurrency = "USD", activeOnly = true))
        assertNotEquals(deletedId, activeId)
    }

    private fun countRows(
        targetCurrency: String,
        activeOnly: Boolean = true,
    ): Int {
        val deletedCondition = if (activeOnly) "and deleted_at is null" else ""
        return requireNotNull(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from exchange_rates
                where base_currency = ?
                  and target_currency = ?
                  and rate_date = ?
                  $deletedCondition
                """.trimIndent(),
                Int::class.java,
                BASE_CURRENCY,
                targetCurrency,
                RATE_DATE,
            )
        )
    }

    private fun findId(targetCurrency: String): Long {
        return requireNotNull(
            jdbcTemplate.queryForObject(
                """
                select id
                from exchange_rates
                where base_currency = ?
                  and target_currency = ?
                  and rate_date = ?
                  and deleted_at is null
                """.trimIndent(),
                Long::class.java,
                BASE_CURRENCY,
                targetCurrency,
                RATE_DATE,
            )
        )
    }

    private fun row(
        targetCurrency: String,
        rate: String,
        source: String = "KOREA_EXIM",
    ): ExchangeRateImportRow {
        return ExchangeRateImportRow(
            baseCurrency = BASE_CURRENCY,
            targetCurrency = targetCurrency,
            rate = BigDecimal(rate),
            rateDate = RATE_DATE,
            source = source,
        )
    }

    companion object {
        private const val BASE_CURRENCY = "KRW"
        private val RATE_DATE: LocalDate = LocalDate.of(2026, 7, 22)
    }
}
