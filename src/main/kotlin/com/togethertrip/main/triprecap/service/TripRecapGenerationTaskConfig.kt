package com.togethertrip.main.triprecap.service

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class TripRecapGenerationTaskConfig {

    @Bean
    fun tripRecapGenerationTaskExecutor(): TaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 100
            setThreadNamePrefix("trip-recap-generation-")
            initialize()
        }
    }
}
