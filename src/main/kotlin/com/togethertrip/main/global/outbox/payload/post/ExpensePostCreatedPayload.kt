package com.togethertrip.main.global.outbox.payload.post

import com.togethertrip.main.global.outbox.payload.common.DefaultOutboxRecipientPayload
import com.togethertrip.main.global.outbox.payload.common.OutboxNotificationPayload
import java.math.BigDecimal
import java.time.Instant

data class ExpensePostCreatedPayload(
    override val recipients: List<DefaultOutboxRecipientPayload>,
    val actorUserId: Long,
    val tripId: Long,
    val postId: Long,
    val transactionId: Long,
    val tripName: String,
    val actorDisplayName: String,
    val postType: String,
    val title: String?,
    val amount: BigDecimal,
    val currency: String,
    val baseAmount: BigDecimal?,
    val baseCurrency: String?,
    override val occurredAt: Instant,
    override val eventVersion: Int = 1,
) : OutboxNotificationPayload<DefaultOutboxRecipientPayload> {

    override fun withRecipients(recipients: List<DefaultOutboxRecipientPayload>): ExpensePostCreatedPayload =
        copy(recipients = recipients)
}
