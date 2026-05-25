package com.togethertrip.main.settlement.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.user.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant

/**
 * 정산 스냅샷.
 * DRAFT 는 여러 개 존재 가능, CONFIRMED 는 여행방당 하나만 가능
 * (uk_settlements_confirmed_trip 부분 유니크 인덱스로 강제).
 */
@Entity
@Table(name = "settlements")
class Settlement(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SettlementStatus,

    // 정산 계산 기준이 된 여행방 거래 버전 (trips.expense_version 스냅샷)
    @Column(name = "trip_expense_version", nullable = false)
    var tripExpenseVersion: Long,

    // 정산 계산 엔진 버전
    @Column(name = "calculation_version", nullable = false, length = 30)
    var calculationVersion: String,

    @Column(name = "base_currency", nullable = false, length = 3)
    var baseCurrency: String,

    @Column(name = "total_expense_amount", nullable = false, precision = 19, scale = 2)
    var totalExpenseAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_share_amount", nullable = false, precision = 19, scale = 2)
    var totalShareAmount: BigDecimal = BigDecimal.ZERO,

    // 정산 결과 스냅샷 (JSONB). 참여자별 잔액, 송금 계획 등을 직렬화하여 저장
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_payload", nullable = false, columnDefinition = "jsonb")
    var snapshotPayload: String,

    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_user_id")
    var confirmedBy: User? = null,

    @Column(name = "share_token", length = 100)
    var shareToken: String? = null,

) : BaseEntity()
