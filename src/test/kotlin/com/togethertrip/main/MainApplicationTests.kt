package com.togethertrip.main

import com.togethertrip.main.global.config.MainIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.security.core.userdetails.UserDetailsService
import kotlin.test.assertTrue

@MainIntegrationTest
class MainApplicationTests @Autowired constructor(
    private val applicationContext: ApplicationContext,
) {

    @Test
    fun contextLoads() {
    }

    @Test
    fun `기본 보안 사용자를 자동 생성하지 않는다`() {
        assertTrue(applicationContext.getBeansOfType(UserDetailsService::class.java).isEmpty())
    }

}
