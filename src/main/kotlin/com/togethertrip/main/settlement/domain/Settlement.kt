package com.togethertrip.main.settlement.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.domain.Trip
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "settlements")
class Settlement(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SettlementStatus,

    @Column(name = "total_expense_amount", nullable = false, precision = 19, scale = 2)
    var totalExpenseAmount: BigDecimal,

    @Column(name = "total_common_fund_amount", nullable = false, precision = 19, scale = 2)
    var totalCommonFundAmount: BigDecimal,

    @Column(name = "share_token", length = 100)
    var shareToken: String? = null,

) : BaseEntity()
