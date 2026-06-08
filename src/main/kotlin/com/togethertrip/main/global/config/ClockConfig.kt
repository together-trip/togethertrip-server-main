package com.togethertrip.main.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock {
        return Clock.system(SEOUL_ZONE)
    }

    companion object {
        private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
