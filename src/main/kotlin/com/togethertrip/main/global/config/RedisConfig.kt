package com.togethertrip.main.global.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedisConfig(
    private val redisConnectionDetails: DataRedisConnectionDetails,
) {

    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        return Redisson.create(redissonConfig())
    }

    internal fun redissonConfig(): Config {
        val standalone = requireNotNull(redisConnectionDetails.standalone) {
            "Redisson requires standalone Redis connection details"
        }
        val redisPassword = redisConnectionDetails.password
        val config = Config()
        val singleServerConfig = config.useSingleServer()
            .setAddress("redis://${standalone.host}:${standalone.port}")

        if (!redisPassword.isNullOrBlank()) {
            singleServerConfig.setPassword(redisPassword)
        }

        return config
    }
}
