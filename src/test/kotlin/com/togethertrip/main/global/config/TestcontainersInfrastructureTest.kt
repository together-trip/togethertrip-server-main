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
        assertEquals("25", flyway.info().current()?.version?.version)
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'oauth_accounts'
                  and column_name = 'encrypted_refresh_token'
                """.trimIndent(),
                Int::class.java,
            )
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'users'
                  and column_name in (
                    'phone_number',
                    'phone_number_encrypted',
                    'phone_number_encryption_version',
                    'phone_number_masked',
                    'phone_number_hash',
                    'phone_number_hash_version',
                    'phone_verified_at'
                  )
                """.trimIndent(),
                Int::class.java,
            )
        )
        assertEquals(
            2,
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                  and indexname in (
                    'idx_transaction_payments_participant_transaction_active',
                    'idx_transaction_shares_participant_transaction_active'
                  )
                """.trimIndent(),
                Int::class.java,
            )
        )
        assertEquals(
            3,
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('moderation_reports', 'moderation_report_audits', 'user_blocks')
                """.trimIndent(),
                Int::class.java,
            )
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                  and indexname = 'uk_users_verified_phone_hash'
                """.trimIndent(),
                Int::class.java,
            )
        )
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
