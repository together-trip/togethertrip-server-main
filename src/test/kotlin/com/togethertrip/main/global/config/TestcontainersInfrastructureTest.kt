package com.togethertrip.main.global.config

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@MainIntegrationTest
class TestcontainersInfrastructureTest @Autowired constructor(
    private val flyway: Flyway,
    private val jdbcTemplate: JdbcTemplate,
    private val redisTemplate: StringRedisTemplate,
    private val redissonClient: RedissonClient,
    private val postgisContainer: PostgreSQLContainer,
    private val redisContainer: GenericContainer<Nothing>,
) {

    @Test
    fun `빈 PostGIS container에 전체 Flyway migration을 적용한다`() {
        val postgisVersion = jdbcTemplate.queryForObject(
            "select postgis_version()",
            String::class.java,
        )
        val connectedJdbcUrl = requireNotNull(jdbcTemplate.dataSource)
            .connection
            .use { it.metaData.url }

        assertTrue(postgisContainer.isRunning)
        assertEquals(postgisContainer.jdbcUrl, connectedJdbcUrl)
        assertNotNull(postgisVersion)
        assertTrue(postgisVersion.isNotBlank())
        assertEquals("21", flyway.info().current()?.version?.version)
    }

    @Test
    fun `Lettuce와 Redisson은 동일한 Redis container mapped port를 사용한다`() {
        val key = "testcontainers:infrastructure"
        val connectionFactory = redisTemplate.connectionFactory as LettuceConnectionFactory

        try {
            redissonClient.getBucket<String>(key, StringCodec.INSTANCE).set("ready")

            assertTrue(redisContainer.isRunning)
            assertEquals(redisContainer.host, connectionFactory.standaloneConfiguration.hostName)
            assertEquals(redisContainer.firstMappedPort, connectionFactory.standaloneConfiguration.port)
            assertEquals("ready", redisTemplate.opsForValue().get(key))
        } finally {
            redisTemplate.delete(key)
        }
    }
}
