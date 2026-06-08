package com.togethertrip.main.global.logging

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RequestLoggingFilterRegistrationConfig {

    @Bean
    fun requestLoggingFilterRegistration(
        requestLoggingFilter: RequestLoggingFilter,
    ): FilterRegistrationBean<RequestLoggingFilter> {
        return FilterRegistrationBean(requestLoggingFilter).apply {
            isEnabled = false
        }
    }
}
