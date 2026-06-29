package com.togethertrip.main.global.outbox.infrastructure.sqs

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "notification.sqs")
data class SqsOutboxProperties(
    val queueUrl: String = "",
    val region: String = "ap-northeast-2",
    val enabled: Boolean = true,
) {
    fun hasQueueUrl(): Boolean = queueUrl.isNotBlank()
}
