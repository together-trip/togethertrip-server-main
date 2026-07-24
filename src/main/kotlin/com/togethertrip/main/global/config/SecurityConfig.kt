package com.togethertrip.main.global.config

import com.togethertrip.main.global.security.handler.CustomAccessDeniedHandler
import com.togethertrip.main.global.security.handler.CustomAuthenticationEntryPoint
import com.togethertrip.main.global.security.jwt.JwtAuthenticationFilter
import com.togethertrip.main.global.logging.RequestLoggingFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val requestLoggingFilter: RequestLoggingFilter,
    private val authenticationEntryPoint: CustomAuthenticationEntryPoint,
    private val accessDeniedHandler: CustomAccessDeniedHandler,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests {
                it.requestMatchers("/api/admin/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.GET, "/api/terms").permitAll()

                it.requestMatchers(
                    "/api/auth/oauth/kakao",
                    "/api/auth/refresh",
                    "/api/local-test/**",
                    "/api/users/nicknames/availability",
                    "/health",
                    "/actuator/health",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/uploads/post-attachments/**",
                    "/uploads/user-profile-images/**",
                ).permitAll()

                it.anyRequest().authenticated()
            }
            .addFilterBefore(
                requestLoggingFilter,
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .addFilterAt(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .build()
    }
}
