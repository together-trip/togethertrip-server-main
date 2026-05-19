package com.togethertrip.main.global.security.jwt

import com.togethertrip.main.global.security.principal.AuthUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)

        if (token != null &&
            SecurityContextHolder.getContext().authentication == null &&
            jwtTokenProvider.validateToken(token)
        ) {
            val claims = jwtTokenProvider.getClaims(token)

            if (claims.tokenType == TokenType.ACCESS) {
                val authUser = AuthUser(
                    userId = claims.userId,
                    role = claims.role,
                )

                val authentication = UsernamePasswordAuthenticationToken(
                    authUser,
                    null,
                    authUser.getAuthorities(),
                )

                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val authorizationHeader = request.getHeader("Authorization")

        if (authorizationHeader.isNullOrBlank()) {
            return null
        }

        if (!authorizationHeader.startsWith("Bearer ")) {
            return null
        }

        return authorizationHeader.substring(7)
    }
}