package com.togethertrip.main.global.config

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
@Import(MainTestcontainersConfiguration::class)
annotation class MainIntegrationTest
