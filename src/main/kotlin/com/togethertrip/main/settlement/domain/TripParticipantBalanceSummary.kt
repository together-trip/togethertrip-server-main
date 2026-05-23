package com.togethertrip.main.settlement.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal

/**
 * 여행 참여자별 정산 미리보기용 증분 집계.
 * created_at / updated_at / deleted_at 은 BaseEntity 에서 제공한다.
 */
@Entity
@Table(
    name = "trip_participant_balance_summaries",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_balance_summaries_trip_participant",
            columnNames = ["trip_id", "trip_participant_id"],
        ),
    ],
)
class TripParticipantBalanceSummary(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_participant_id", nullable = false)
    var tripParticipant: TripParticipant,

    @Column(name = "paid_base_amount", nullable = false, precision = 19, scale = 2)
    var paidBaseAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "share_base_amount", nullable = false, precision = 19, scale = 2)
    var shareBaseAmount: BigDecimal = BigDecimal.ZERO,

    // 순정산 금액 = paid_base_amount - share_base_amount
    @Column(name = "net_base_amount", nullable = false, precision = 19, scale = 2)
    var netBaseAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "projection_version", nullable = false)
    var projectionVersion: Long = 0,

) : BaseEntity()
