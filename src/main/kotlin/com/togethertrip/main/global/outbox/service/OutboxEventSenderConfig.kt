package com.togethertrip.main.global.outbox.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OutboxEventSenderConfig {

    @Bean
    @ConditionalOnMissingBean(OutboxEventSender::class)
    fun loggingOutboxEventSender(): OutboxEventSender {
        return LoggingOutboxEventSender()
    }
}
