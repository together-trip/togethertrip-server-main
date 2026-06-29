package com.togethertrip.main.global.outbox.infrastructure.sqs

import com.togethertrip.main.global.outbox.service.LoggingOutboxEventSender
import com.togethertrip.main.global.outbox.service.OutboxEventSender
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient

@Configuration
@EnableConfigurationProperties(SqsOutboxProperties::class, SqsOutboxDispatchProperties::class)
class SqsOutboxEventSenderConfig {

    @Bean
    fun outboxEventSender(
        properties: SqsOutboxProperties,
        objectMapper: ObjectMapper,
    ): OutboxEventSender {
        if (!properties.enabled || !properties.hasQueueUrl()) {
            return LoggingOutboxEventSender()
        }

        return SqsOutboxEventSender(
            sqsClient = SqsClient.builder()
                .region(Region.of(properties.region))
                .build(),
            objectMapper = objectMapper,
            properties = properties,
        )
    }
}
