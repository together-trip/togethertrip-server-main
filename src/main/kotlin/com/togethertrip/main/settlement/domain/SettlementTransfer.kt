package com.togethertrip.main.settlement.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.domain.TripParticipant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "settlement_transfers")
class SettlementTransfer(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    var settlement: Settlement,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    var sender: TripParticipant,

    // DB 컬럼명 오타(recevier_id)를 그대로 유지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recevier_id", nullable = false)
    var receiver: TripParticipant,

    @Column(name = "sender_confirmed_at")
    var senderConfirmedAt: LocalDateTime? = null,

    @Column(name = "recevier_confirmed_at")
    var receiverConfirmedAt: LocalDateTime? = null,

    @Column(nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SettlementTransferStatus,

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

) : BaseEntity()
