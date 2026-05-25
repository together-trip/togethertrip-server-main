package com.togethertrip.main.trip.domain

import com.togethertrip.main.global.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "trip_exchange_rates")
class TripExchangeRate(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    @Column(name = "base_currency", nullable = false, length = 3)
    var baseCurrency: String,

    @Column(name = "target_currency", nullable = false, length = 3)
    var targetCurrency: String,

    @Column(nullable = false, precision = 19, scale = 6)
    var rate: BigDecimal,

    @Column(name = "rate_date")
    var rateDate: LocalDate? = null,

    @Column(length = 50)
    var source: String? = null,

) : BaseEntity()
