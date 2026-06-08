package com.togethertrip.main.trip.domain

import com.togethertrip.main.global.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "exchange_rates")
@SQLRestriction("deleted_at IS NULL")
class ExchangeRate(

    @Column(name = "base_currency", nullable = false, length = 3)
    var baseCurrency: String,

    @Column(name = "target_currency", nullable = false, length = 3)
    var targetCurrency: String,

    @Column(nullable = false, precision = 19, scale = 6)
    var rate: BigDecimal,

    @Column(name = "rate_date", nullable = false)
    var rateDate: LocalDate,

    @Column(length = 50)
    var source: String? = null,

) : BaseEntity()
