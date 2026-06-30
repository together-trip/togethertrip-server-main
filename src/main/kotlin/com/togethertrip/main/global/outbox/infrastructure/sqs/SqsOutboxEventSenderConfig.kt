package com.togethertrip.main.global.outbox.infrastructure.sqs

import com.togethertrip.main.global.outbox.service.LoggingOutboxEventSender
import com.togethertrip.main.global.outbox.service.OutboxEventSender
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import tools.jackson.databind.ObjectMapper

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

        val sqsClientBuilder = SqsClient.builder()
            .region(Region.of(properties.region))

        if (properties.hasStaticCredentials()) {
            val credentials = if (properties.hasSessionToken()) {
                AwsSessionCredentials.create(
                    properties.accessKeyId,
                    properties.secretAccessKey,
                    properties.sessionToken,
                )
            } else {
                AwsBasicCredentials.create(properties.accessKeyId, properties.secretAccessKey)
            }

            sqsClientBuilder.credentialsProvider(StaticCredentialsProvider.create(credentials))
        }

        return SqsOutboxEventSender(
            sqsClient = sqsClientBuilder.build(),
            objectMapper = objectMapper,
            properties = properties,
        )
    }
}
