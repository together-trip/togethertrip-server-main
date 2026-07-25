package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.SettlementTransferRow
import java.math.BigDecimal
import java.time.Instant

data class SettlementTransferRowData(
    private val id: Long,
    private val settlementId: Long,
    private val tripName: String,
    private val senderParticipantId: Long,
    private val senderUserId: Long?,
    private val senderDisplayName: String,
    private val senderUserStatus: String?,
    private val receiverParticipantId: Long,
    private val receiverUserId: Long?,
    private val receiverDisplayName: String,
    private val receiverUserStatus: String?,
    private val amount: BigDecimal,
    private val currency: String,
    private val status: String,
    private val senderConfirmedAt: Instant?,
    private val receiverConfirmedAt: Instant?,
    private val completedAt: Instant?,
    private val autoConfirmed: Boolean,
) : SettlementTransferRow {
    override fun getId() = id
    override fun getSettlementId() = settlementId
    override fun getTripName() = tripName
    override fun getSenderParticipantId() = senderParticipantId
    override fun getSenderUserId() = senderUserId
    override fun getSenderDisplayName() = senderDisplayName
    override fun getSenderUserStatus() = senderUserStatus
    override fun getReceiverParticipantId() = receiverParticipantId
    override fun getReceiverUserId() = receiverUserId
    override fun getReceiverDisplayName() = receiverDisplayName
    override fun getReceiverUserStatus() = receiverUserStatus
    override fun getAmount() = amount
    override fun getCurrency() = currency
    override fun getStatus() = status
    override fun getSenderConfirmedAt() = senderConfirmedAt
    override fun getReceiverConfirmedAt() = receiverConfirmedAt
    override fun getCompletedAt() = completedAt
    override fun getAutoConfirmed() = autoConfirmed
}
