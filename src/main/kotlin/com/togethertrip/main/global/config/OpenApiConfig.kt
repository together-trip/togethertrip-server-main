package com.togethertrip.main.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI {
        val bearerScheme = SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .`in`(SecurityScheme.In.HEADER)
            .name("Authorization")

        return OpenAPI()
            .info(
                Info()
                    .title("TogetherTrip API")
                    .description("TogetherTrip 서버 API 명세")
                    .version("v1")
            )
            .components(
                Components().addSecuritySchemes("bearerAuth", bearerScheme)
            )
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
    }
}
