package com.togethertrip.main.global.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedisConfig(
    @Value("\${spring.data.redis.host:127.0.0.1}")
    private val redisHost: String,
    @Value("\${spring.data.redis.port:6379}")
    private val redisPort: Int,
    @Value("\${spring.data.redis.password:}")
    private val redisPassword: String,
) {

    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        val config = Config()
        val singleServerConfig = config.useSingleServer()
            .setAddress("redis://$redisHost:$redisPort")

        if (redisPassword.isNotBlank()) {
            singleServerConfig.setPassword(redisPassword)
        }

        return Redisson.create(config)
    }
}
