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
            .description(
                "JWT access token을 입력합니다. local 프로필에서는 개발용 고정 토큰을 사용할 수 있습니다. " +
                    "전화번호 인증 완료 유저: local-test:verified, 전화번호 미인증 유저: local-test:unverified. " +
                    "여러 테스트 유저가 필요하면 local-test:verified:{id}, local-test:unverified:{id} 형식으로 입력하세요."
            )

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
