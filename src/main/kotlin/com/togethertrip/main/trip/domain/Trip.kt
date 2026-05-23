package com.togethertrip.main.trip.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.user.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "trips")
class Trip(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    var createdBy: User,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "default_currency", nullable = false, length = 3)
    var defaultCurrency: String,

    @Column(name = "start_date")
    var startDate: LocalDate? = null,

    @Column(name = "end_date")
    var endDate: LocalDate? = null,

    // 거래 변경 버전. 정산 스냅샷(settlements.trip_expense_version) 기준 버전으로 사용
    @Column(name = "expense_version", nullable = false)
    var expenseVersion: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 20)
    var settlementStatus: TripSettlementStatus = TripSettlementStatus.OPEN,

    @Column(name = "settled_at")
    var settledAt: Instant? = null,

) : BaseEntity()
