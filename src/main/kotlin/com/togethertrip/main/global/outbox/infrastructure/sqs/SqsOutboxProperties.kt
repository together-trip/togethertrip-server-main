package com.togethertrip.main.global.outbox.infrastructure.sqs

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "notification.sqs")
data class SqsOutboxProperties(
    val queueUrl: String = "",
    val region: String = "ap-northeast-2",
    val enabled: Boolean = true,
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val sessionToken: String = "",
) {
    fun hasQueueUrl(): Boolean = queueUrl.isNotBlank()

    fun hasStaticCredentials(): Boolean = accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()

    fun hasSessionToken(): Boolean = sessionToken.isNotBlank()
}
