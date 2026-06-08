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
import java.time.Instant

/**
 * 확정(CONFIRMED) 정산 기준 송금 체크리스트.
 */
@Entity
@Table(name = "settlement_transfers")
class SettlementTransfer(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    var settlement: Settlement,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_participant_id", nullable = false)
    var sender: TripParticipant,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_participant_id", nullable = false)
    var receiver: TripParticipant,

    @Column(nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal,

    @Column(nullable = false, length = 3)
    var currency: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SettlementTransferStatus,

    @Column(name = "sender_confirmed_at")
    var senderConfirmedAt: Instant? = null,

    @Column(name = "receiver_confirmed_at")
    var receiverConfirmedAt: Instant? = null,

    @Column(name = "auto_confirmed", nullable = false)
    var autoConfirmed: Boolean = false,

    @Column(name = "auto_confirm_reason", length = 100)
    var autoConfirmReason: String? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

) : BaseEntity() {

    fun confirmAsSender(confirmedAt: Instant = Instant.now()) {
        if (senderConfirmedAt != null) {
            return
        }

        senderConfirmedAt = confirmedAt
        status = when (status) {
            SettlementTransferStatus.PENDING -> SettlementTransferStatus.SENDER_CONFIRMED
            SettlementTransferStatus.RECEIVER_CONFIRMED -> SettlementTransferStatus.COMPLETED
            else -> status
        }
        if (status == SettlementTransferStatus.COMPLETED && completedAt == null) {
            completedAt = confirmedAt
        }
        updatedAt = confirmedAt
    }

    fun confirmAsReceiver(confirmedAt: Instant = Instant.now()) {
        if (receiverConfirmedAt != null) {
            return
        }

        receiverConfirmedAt = confirmedAt
        status = when (status) {
            SettlementTransferStatus.PENDING -> SettlementTransferStatus.RECEIVER_CONFIRMED
            SettlementTransferStatus.SENDER_CONFIRMED -> SettlementTransferStatus.COMPLETED
            else -> status
        }
        if (status == SettlementTransferStatus.COMPLETED && completedAt == null) {
            completedAt = confirmedAt
        }
        updatedAt = confirmedAt
    }

    fun autoConfirmSender(
        reason: String,
        confirmedAt: Instant = Instant.now(),
    ) {
        val wasConfirmed = senderConfirmedAt != null
        confirmAsSender(confirmedAt)
        if (!wasConfirmed) {
            autoConfirmed = true
            autoConfirmReason = reason
        }
    }

    fun autoConfirmReceiver(
        reason: String,
        confirmedAt: Instant = Instant.now(),
    ) {
        val wasConfirmed = receiverConfirmedAt != null
        confirmAsReceiver(confirmedAt)
        if (!wasConfirmed) {
            autoConfirmed = true
            autoConfirmReason = reason
        }
    }
}
