package com.togethertrip.main.auth.client

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AppleOAuthProperties::class)
class AppleOAuthConfig
