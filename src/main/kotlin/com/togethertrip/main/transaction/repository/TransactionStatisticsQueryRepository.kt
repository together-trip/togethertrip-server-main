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
        // 공동경비 충전과 사용 거래만 잔액 계산 대상으로 집계한다.
        val row = entityManager.createNativeQuery(
            """
            select min(tx.base_currency) as base_currency,
                   coalesce(sum(case when tx.transaction_type = 'FUND_CHARGE' then tx.base_amount else 0 end), 0) as charged_base_amount,
                   coalesce(sum(case when tx.transaction_type = 'FUND_USE' then tx.base_amount else 0 end), 0) as used_base_amount
            from transactions tx
            where tx.deleted_at is null
              and tx.trip_id = :tripId
              and tx.status = :status
            """.trimIndent()
        )
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
    ): List<TransactionStatisticsRow> {
        // 거래 유형별 통계는 거래 원장의 기준 통화 금액을 합산한다.
        return entityManager.createNativeQuery(
            """
            select tx.transaction_type as item_key,
                   tx.transaction_type as item_label,
                   count(tx.id) as transaction_count,
                   coalesce(sum(tx.base_amount), 0) as total_base_amount
            from transactions tx
            left join posts post on post.id = (
                select min(candidate.id)
                from posts candidate
                where candidate.transaction_id = tx.id
                  and candidate.deleted_at is null
            )
            where tx.deleted_at is null
              and tx.trip_id = :tripId
              and tx.status = :status
              and (:fromFilterEnabled = false or coalesce(post.occurred_at, tx.created_at) >= :from)
              and (:toFilterEnabled = false or coalesce(post.occurred_at, tx.created_at) < :toExclusive)
            group by tx.transaction_type
            order by total_base_amount desc, item_key asc
            """.trimIndent()
        )
            .setCommonParameters(
                tripId = tripId,
                status = status,
                from = from,
                toExclusive = toExclusive,
            )
            .resultList
            .map(::toStatisticsRow)
    }

    fun findCategoryStatistics(
        tripId: Long,
        from: Instant?,
        toExclusive: Instant?,
        status: TransactionStatus = TransactionStatus.ACTIVE,
    ): List<TransactionStatisticsRow> {
        // 카테고리와 발생일은 연결된 거래 게시글 정보를 우선 사용한다.
        return entityManager.createNativeQuery(
            """
            select coalesce(nullif(trim(post.category), ''), 'UNCATEGORIZED') as item_key,
                   coalesce(nullif(trim(post.category), ''), 'UNCATEGORIZED') as item_label,
                   count(tx.id) as transaction_count,
                   coalesce(sum(tx.base_amount), 0) as total_base_amount
            from transactions tx
            left join posts post on post.id = (
                select min(candidate.id)
                from posts candidate
                where candidate.transaction_id = tx.id
                  and candidate.deleted_at is null
            )
            where tx.deleted_at is null
              and tx.trip_id = :tripId
              and tx.status = :status
              and (:fromFilterEnabled = false or coalesce(post.occurred_at, tx.created_at) >= :from)
              and (:toFilterEnabled = false or coalesce(post.occurred_at, tx.created_at) < :toExclusive)
            group by coalesce(nullif(trim(post.category), ''), 'UNCATEGORIZED')
            order by total_base_amount desc, item_key asc
            """.trimIndent()
        )
            .setCommonParameters(
                tripId = tripId,
                status = status,
                from = from,
                toExclusive = toExclusive,
            )
            .resultList
            .map(::toStatisticsRow)
    }

    fun findParticipantShareStatistics(
        tripId: Long,
        from: Instant?,
        toExclusive: Instant?,
        status: TransactionStatus = TransactionStatus.ACTIVE,
    ): List<TransactionStatisticsRow> {
        // 참여자 통계는 정산 관점에 맞춰 부담자 share 기준으로 집계한다.
        return entityManager.createNativeQuery(
            """
            select cast(participant.id as varchar) as item_key,
                   participant.display_name as item_label,
                   count(distinct tx.id) as transaction_count,
                   coalesce(sum(share.base_share_amount), 0) as total_base_amount
            from transaction_shares share
            join transactions tx on tx.id = share.transaction_id
            join trip_participants participant on participant.id = share.trip_participant_id
            left join posts post on post.id = (
                select min(candidate.id)
                from posts candidate
                where candidate.transaction_id = tx.id
                  and candidate.deleted_at is null
            )
            where share.deleted_at is null
              and tx.deleted_at is null
              and tx.trip_id = :tripId
              and tx.status = :status
              and (:fromFilterEnabled = false or coalesce(post.occurred_at, tx.created_at) >= :from)
              and (:toFilterEnabled = false or coalesce(post.occurred_at, tx.created_at) < :toExclusive)
            group by participant.id, participant.display_name
            order by total_base_amount desc, participant.id asc
            """.trimIndent()
        )
            .setCommonParameters(
                tripId = tripId,
                status = status,
                from = from,
                toExclusive = toExclusive,
            )
            .resultList
            .map(::toStatisticsRow)
    }

    private fun Query.setCommonParameters(
        tripId: Long,
        status: TransactionStatus,
        from: Instant?,
        toExclusive: Instant?,
    ): Query {
        // null 기간 조건은 boolean 파라미터로 비활성화한다.
        return this
            .setParameter("tripId", tripId)
            .setParameter("status", status.name)
            .setParameter("fromFilterEnabled", from != null)
            .setParameter("from", from ?: Instant.EPOCH)
            .setParameter("toFilterEnabled", toExclusive != null)
            .setParameter("toExclusive", toExclusive ?: Instant.EPOCH)
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
}
