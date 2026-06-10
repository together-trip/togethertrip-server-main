package com.togethertrip.main.global.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Path

@Configuration
class StaticResourceConfig(
    @Value("\${post.attachments.local-storage-path:./uploads/post-attachments}")
    private val postAttachmentStoragePath: String,
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val storageLocation = Path.of(postAttachmentStoragePath)
            .toAbsolutePath()
            .normalize()
            .toUri()
            .toString()
            .let { location ->
                if (location.endsWith("/")) location else "$location/"
            }

        registry
            .addResourceHandler("/uploads/post-attachments/**")
            .addResourceLocations(storageLocation)
    }
}
