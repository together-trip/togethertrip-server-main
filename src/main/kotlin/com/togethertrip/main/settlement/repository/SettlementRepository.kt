package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface SettlementRepository : JpaRepository<Settlement, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): Settlement?

    fun findByTripIdAndStatusAndDeletedAtIsNull(
        tripId: Long,
        status: SettlementStatus,
    ): List<Settlement>

    // CONFIRMED 정산은 여행방당 하나만 존재 (uk_settlements_confirmed_trip)
    fun findFirstByTripIdAndStatusAndDeletedAtIsNull(
        tripId: Long,
        status: SettlementStatus,
    ): Settlement?

    fun findByShareTokenAndDeletedAtIsNull(shareToken: String): Settlement?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
        update settlements
        set share_token = :shareToken,
            updated_at = :updatedAt
        where id = :settlementId
          and deleted_at is null
          and share_token is null
        """,
        nativeQuery = true
    )
    fun updateShareTokenIfAbsent(
        @Param("settlementId") settlementId: Long,
        @Param("shareToken") shareToken: String,
        @Param("updatedAt") updatedAt: Instant,
    ): Int

    // 회전은 기존 토큰을 새 값으로 덮어써 이전 공유 링크를 무효화한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
        update settlements
        set share_token = :shareToken,
            updated_at = :updatedAt
        where id = :settlementId
          and deleted_at is null
        """,
        nativeQuery = true
    )
    fun rotateShareToken(
        @Param("settlementId") settlementId: Long,
        @Param("shareToken") shareToken: String,
        @Param("updatedAt") updatedAt: Instant,
    ): Int
}
