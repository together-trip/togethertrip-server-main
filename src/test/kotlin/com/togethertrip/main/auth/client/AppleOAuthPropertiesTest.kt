package com.togethertrip.main.auth.client

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertFailsWith

class AppleOAuthPropertiesTest {
    @Test
    fun `Apple connect timeout은 1ms 이상 30초 이하여야 한다`() {
        assertFailsWith<IllegalArgumentException> {
            AppleOAuthProperties(connectTimeout = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            AppleOAuthProperties(connectTimeout = Duration.ofSeconds(31))
        }
    }

    @Test
    fun `Apple response timeout은 1ms 이상 30초 이하여야 한다`() {
        assertFailsWith<IllegalArgumentException> {
            AppleOAuthProperties(responseTimeout = Duration.ofNanos(1))
        }
        assertFailsWith<IllegalArgumentException> {
            AppleOAuthProperties(responseTimeout = Duration.ofSeconds(31))
        }
    }
}
