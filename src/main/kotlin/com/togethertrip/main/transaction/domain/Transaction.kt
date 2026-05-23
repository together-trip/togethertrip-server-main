package com.togethertrip.main.transaction.domain

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
import jakarta.persistence.Version
import org.locationtech.jts.geom.Point
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "transactions")
class Transaction(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    // DDL 기준: created_by_user_id 는 users(id) 를 참조한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    var createdBy: User,

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

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant,

    @Column(name = "location", columnDefinition = "GEOGRAPHY(POINT, 4326)")
    var location: Point? = null,

    @Column(name = "place_name", length = 100)
    var placeName: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TransactionStatus = TransactionStatus.ACTIVE,

) : BaseEntity() {

    // 거래 낙관적 락 버전 (DDL: version BIGINT NOT NULL DEFAULT 0)
    @Version
    @Column(nullable = false)
    var version: Long = 0
}
