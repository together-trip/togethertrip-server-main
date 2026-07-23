package com.togethertrip.main.global.config

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RedisConfigTest {

    @Test
    fun `connection details의 host port password로 Redisson을 설정한다`() {
        val connectionDetails = connectionDetails(password = "secret")

        val singleServerConfig = RedisConfig(connectionDetails)
            .redissonConfig()
            .useSingleServer()

        assertEquals("redis://redis.internal:16379", singleServerConfig.address)
        assertEquals("secret", singleServerConfig.password)
    }

    @Test
    fun `password가 없으면 Redisson 인증을 설정하지 않는다`() {
        val connectionDetails = connectionDetails(password = null)

        val singleServerConfig = RedisConfig(connectionDetails)
            .redissonConfig()
            .useSingleServer()

        assertEquals("redis://redis.internal:16379", singleServerConfig.address)
        assertNull(singleServerConfig.password)
    }

    @Test
    fun `standalone connection details가 없으면 설정 생성을 거부한다`() {
        val connectionDetails = mock(DataRedisConnectionDetails::class.java)
        `when`(connectionDetails.standalone).thenReturn(null)

        assertFailsWith<IllegalArgumentException> {
            RedisConfig(connectionDetails).redissonConfig()
        }
    }

    private fun connectionDetails(password: String?): DataRedisConnectionDetails {
        val connectionDetails = mock(DataRedisConnectionDetails::class.java)
        val standalone = mock(DataRedisConnectionDetails.Standalone::class.java)
        `when`(connectionDetails.standalone).thenReturn(standalone)
        `when`(connectionDetails.password).thenReturn(password)
        `when`(standalone.host).thenReturn("redis.internal")
        `when`(standalone.port).thenReturn(16379)
        return connectionDetails
    }
}
