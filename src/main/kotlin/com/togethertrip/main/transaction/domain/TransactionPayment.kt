package com.togethertrip.main.transaction.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.domain.TripParticipant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "transaction_payments")
class TransactionPayment(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    var transaction: Transaction,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_participant_id", nullable = false)
    var tripParticipant: TripParticipant,

    @Column(nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal,

    @Column(nullable = false, length = 3)
    var currency: String,

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6)
    var exchangeRate: BigDecimal,

    @Column(name = "base_currency", nullable = false, length = 3)
    var baseCurrency: String,

    @Column(name = "base_amount", nullable = false, precision = 19, scale = 2)
    var baseAmount: BigDecimal,

) : BaseEntity()
