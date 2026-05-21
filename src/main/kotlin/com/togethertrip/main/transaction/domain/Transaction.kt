package com.togethertrip.main.transaction.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.locationtech.jts.geom.Point
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "transactions")
class Transaction(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    // created_by_user_id 컬럼이 trip_participants.id 를 참조함 (스키마 FK 기준)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    var createdBy: TripParticipant,

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    var transactionType: TransactionType,

    @Column(nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal,

    @Column(nullable = false, length = 3)
    var currency: String,

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6)
    var exchangeRate: BigDecimal,

    @Column(name = "base_amount", nullable = false, precision = 19, scale = 2)
    var baseAmount: BigDecimal,

    @Column(length = 30)
    var category: String? = null,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "occured_at", nullable = false)
    var occuredAt: LocalDateTime,

    @Column(name = "location", columnDefinition = "GEOGRAPHY(POINT, 4326)")
    var location: Point? = null,

    @Column(name = "place_name", length = 100)
    var placeName: String? = null,

) : BaseEntity()
