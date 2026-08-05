package com.togethertrip.main.global.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Path

@Configuration
class StaticResourceConfig(
    @Value("\${user.profile-images.local-storage-path}")
    private val userProfileImageStoragePath: String,
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/uploads/user-profile-images/**")
            .addResourceLocations(toResourceLocation(userProfileImageStoragePath))
    }

    private fun toResourceLocation(storagePath: String): String {
        return Path.of(storagePath)
            .toAbsolutePath()
            .normalize()
            .toUri()
            .toString()
            .let { location ->
                if (location.endsWith("/")) location else "$location/"
            }
    }
}
