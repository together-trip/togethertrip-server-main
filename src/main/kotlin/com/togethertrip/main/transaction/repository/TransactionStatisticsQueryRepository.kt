package com.togethertrip.main.transaction.repository

import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.repository.projection.CommonFundBalanceRow
import com.togethertrip.main.transaction.repository.projection.TransactionStatisticsRow
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant

@Repository
class TransactionStatisticsQueryRepository(
    private val entityManager: EntityManager,
) {
    fun findCommonFundBalance(
        tripId: Long,
        status: TransactionStatus = TransactionStatus.ACTIVE,
    ): CommonFundBalanceRow {
        val row = entityManager.createNativeQuery(COMMON_FUND_QUERY)
            .setParameter("tripId", tripId)
            .setParameter("status", status.name)
            .singleResult as Array<*>

        return CommonFundBalanceRow(
            baseCurrency = row[0] as String?,
            chargedBaseAmount = row[1] as BigDecimal,
            usedBaseAmount = row[2] as BigDecimal,
        )
    }

    fun findTypeStatistics(
        tripId: Long,
        from: Instant?,
        toExclusive: Instant?,
        status: TransactionStatus = TransactionStatus.ACTIVE,
    ): List<TransactionStatisticsRow> = findStatistics(
        baseSql = TYPE_STATISTICS_QUERY,
        tripId = tripId,
        from = from,
        toExclusive = toExclusive,
        status = status,
    )

    fun findCategoryStatistics(
        tripId: Long,
        from: Instant?,
        toExclusive: Instant?,
        status: TransactionStatus = TransactionStatus.ACTIVE,
    ): List<TransactionStatisticsRow> = findStatistics(
        baseSql = CATEGORY_STATISTICS_QUERY,
        tripId = tripId,
        from = from,
        toExclusive = toExclusive,
        status = status,
    )

    fun findParticipantShareStatistics(
        tripId: Long,
        from: Instant?,
        toExclusive: Instant?,
        status: TransactionStatus = TransactionStatus.ACTIVE,
    ): List<TransactionStatisticsRow> = findStatistics(
        baseSql = PARTICIPANT_STATISTICS_QUERY,
        tripId = tripId,
        from = from,
        toExclusive = toExclusive,
        status = status,
    )

    private fun findStatistics(
        baseSql: String,
        tripId: Long,
        from: Instant?,
        toExclusive: Instant?,
        status: TransactionStatus,
    ): List<TransactionStatisticsRow> {
        val query = entityManager.createNativeQuery(addPeriodShape(baseSql, from, toExclusive))
            .setParameter("tripId", tripId)
            .setParameter("status", status.name)
            .setPeriodParameters(from, toExclusive)

        return query.resultList.map(::toStatisticsRow)
    }

    private fun addPeriodShape(baseSql: String, from: Instant?, toExclusive: Instant?): String {
        val predicates = buildList {
            if (from != null) {
                add("(tx.occurred_at >= :from or (tx.occurred_at is null and tx.created_at >= :from))")
            }
            if (toExclusive != null) {
                add("(tx.occurred_at < :toExclusive or (tx.occurred_at is null and tx.created_at < :toExclusive))")
            }
        }
        val periodSql = if (predicates.isEmpty()) {
            ""
        } else {
            predicates.joinToString(separator = " and ", prefix = " and ")
        }
        return baseSql.replace(PERIOD_MARKER, periodSql)
    }

    private fun Query.setPeriodParameters(from: Instant?, toExclusive: Instant?): Query {
        from?.let { setParameter("from", it) }
        toExclusive?.let { setParameter("toExclusive", it) }
        return this
    }

    private fun toStatisticsRow(row: Any?): TransactionStatisticsRow {
        val values = row as Array<*>
        return TransactionStatisticsRow(
            key = values[0].toString(),
            label = values[1].toString(),
            transactionCount = (values[2] as Number).toLong(),
            totalBaseAmount = values[3] as BigDecimal,
        )
    }

    private companion object {
        const val PERIOD_MARKER = "/* period predicates */"

        val COMMON_FUND_QUERY = """
            select min(tx.base_currency) as base_currency,
                   coalesce(sum(case when tx.transaction_type = 'FUND_CHARGE' then tx.base_amount else 0 end), 0) as charged_base_amount,
                   coalesce(sum(case when tx.transaction_type = 'FUND_USE' then tx.base_amount else 0 end), 0) as used_base_amount
            from transactions tx
            where tx.deleted_at is null
              and tx.trip_id = :tripId
              and tx.status = :status
              and tx.transaction_type in ('FUND_CHARGE', 'FUND_USE')
        """.trimIndent()

        val TYPE_STATISTICS_QUERY = """
            select tx.transaction_type as item_key,
                   tx.transaction_type as item_label,
                   count(tx.id) as transaction_count,
                   coalesce(sum(tx.base_amount), 0) as total_base_amount
            from transactions tx
            where tx.deleted_at is null
              and tx.trip_id = :tripId
              and tx.status = :status
              $PERIOD_MARKER
            group by tx.transaction_type
            order by total_base_amount desc, item_key asc
        """.trimIndent()

        val CATEGORY_STATISTICS_QUERY = """
            select coalesce(tx.category, 'UNCATEGORIZED') as item_key,
                   coalesce(tx.category, 'UNCATEGORIZED') as item_label,
                   count(*) as transaction_count,
                   coalesce(sum(tx.base_amount), 0) as total_base_amount
            from transactions tx
            where tx.deleted_at is null
              and tx.trip_id = :tripId
              and tx.status = :status
              $PERIOD_MARKER
            group by tx.category
            order by total_base_amount desc, item_key asc
        """.trimIndent()

        val PARTICIPANT_STATISTICS_QUERY = """
            select cast(participant.id as varchar) as item_key,
                   participant.display_name as item_label,
                   count(distinct tx.id) as transaction_count,
                   coalesce(sum(share.base_share_amount), 0) as total_base_amount
            from transaction_shares share
            join transactions tx on tx.id = share.transaction_id
            join trip_participants participant on participant.id = share.trip_participant_id
            where share.deleted_at is null
              and tx.deleted_at is null
              and tx.trip_id = :tripId
              and tx.status = :status
              $PERIOD_MARKER
            group by participant.id, participant.display_name
            order by total_base_amount desc, participant.id asc
        """.trimIndent()
    }
}
