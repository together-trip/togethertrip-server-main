package com.togethertrip.main.global.outbox.infrastructure.sqs

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "outbox.dispatch")
data class SqsOutboxDispatchProperties(
    val enabled: Boolean = true,
    val fixedDelay: Duration = Duration.ofSeconds(3),
    val limit: Int = 50,
    val maxAttempts: Int = 5,
)
