package com.togethertrip.main.global.outbox.payload.post

import com.togethertrip.main.global.outbox.payload.common.DefaultOutboxRecipientPayload
import com.togethertrip.main.global.outbox.payload.common.OutboxNotificationPayload
import java.time.Instant

data class PostCommentCreatedPayload(
    override val recipients: List<DefaultOutboxRecipientPayload>,
    val actorUserId: Long,
    val tripId: Long,
    val postId: Long,
    val commentId: Long,
    val tripName: String,
    val actorDisplayName: String,
    val postTitle: String?,
    override val occurredAt: Instant,
    override val eventVersion: Int = 1,
) : OutboxNotificationPayload<DefaultOutboxRecipientPayload> {

    override fun withRecipients(recipients: List<DefaultOutboxRecipientPayload>): PostCommentCreatedPayload =
        copy(recipients = recipients)
}
