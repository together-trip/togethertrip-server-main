package com.togethertrip.main.settlement.domain

import java.math.BigDecimal
import java.time.Instant

interface SettlementTransferRow {
    fun getId(): Long

    fun getSettlementId(): Long

    fun getTripName(): String

    fun getSenderParticipantId(): Long

    fun getSenderUserId(): Long?

    fun getSenderDisplayName(): String

    fun getSenderUserStatus(): String?

    fun getReceiverParticipantId(): Long

    fun getReceiverUserId(): Long?

    fun getReceiverDisplayName(): String

    fun getReceiverUserStatus(): String?

    fun getAmount(): BigDecimal

    fun getCurrency(): String

    fun getStatus(): String

    fun getSenderConfirmedAt(): Instant?

    fun getReceiverConfirmedAt(): Instant?

    fun getCompletedAt(): Instant?

    fun getAutoConfirmed(): Boolean
}
