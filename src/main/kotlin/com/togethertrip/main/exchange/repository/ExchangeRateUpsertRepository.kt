package com.togethertrip.main.exchange.repository

import com.togethertrip.main.exchange.domain.ExchangeRateImportRow
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ExchangeRateUpsertRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {

    @Transactional
    fun upsertAll(rows: List<ExchangeRateImportRow>): Int {
        if (rows.isEmpty()) {
            return 0
        }

        val parameters = MapSqlParameterSource()
        val valuesClause = rows.mapIndexed { index, row ->
            parameters
                .addValue("baseCurrency$index", row.baseCurrency)
                .addValue("targetCurrency$index", row.targetCurrency)
                .addValue("rate$index", row.rate)
                .addValue("rateDate$index", row.rateDate)
                .addValue("source$index", row.source)

            """
            (
                :baseCurrency$index,
                :targetCurrency$index,
                :rate$index,
                :rateDate$index,
                :source$index,
                now(),
                now(),
                NULL
            )
            """.trimIndent()
        }.joinToString(",\n")

        return jdbcTemplate.update(
            """
            INSERT INTO exchange_rates (
                base_currency, target_currency, rate, rate_date, source, created_at, updated_at, deleted_at
            )
            VALUES
            $valuesClause
            ON CONFLICT (base_currency, target_currency, rate_date) WHERE deleted_at IS NULL
            DO UPDATE SET
                rate = EXCLUDED.rate,
                source = EXCLUDED.source,
                updated_at = now()
            WHERE exchange_rates.rate IS DISTINCT FROM EXCLUDED.rate
               OR exchange_rates.source IS DISTINCT FROM EXCLUDED.source
            """.trimIndent(),
            parameters,
        )
    }
}
