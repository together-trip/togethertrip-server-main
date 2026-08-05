package com.togethertrip.main.global.outbox.infrastructure.sqs

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "notification.sqs")
data class SqsOutboxProperties(
    val queueUrl: String = "",
    val queueType: SqsOutboxQueueType = SqsOutboxQueueType.STANDARD,
    val region: String = "ap-northeast-2",
    val enabled: Boolean = true,
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val sessionToken: String = "",
) {
    init {
        if (hasQueueUrl()) {
            require(queueType.matches(queueUrl)) {
                "notification.sqs.queue-type=$queueType does not match queue URL suffix"
            }
        }
    }

    fun hasQueueUrl(): Boolean = queueUrl.isNotBlank()

    fun isFifoQueue(): Boolean = queueType == SqsOutboxQueueType.FIFO

    fun hasStaticCredentials(): Boolean = accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()

    fun hasSessionToken(): Boolean = sessionToken.isNotBlank()
}
