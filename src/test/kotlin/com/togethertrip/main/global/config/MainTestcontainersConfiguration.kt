package com.togethertrip.main.global.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class MainTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgisContainer(): PostgreSQLContainer {
        val image = DockerImageName
            .parse("postgis/postgis:17-3.5")
            .asCompatibleSubstituteFor("postgres")

        return PostgreSQLContainer(image).apply {
            withDatabaseName("together_trip_test")
            withUsername("together_trip")
            withPassword("together_trip")
        }
    }

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<Nothing> {
        return GenericContainer<Nothing>(DockerImageName.parse("redis:7"))
            .withExposedPorts(REDIS_PORT)
    }

    companion object {
        private const val REDIS_PORT = 6379
    }
}
