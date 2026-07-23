package com.togethertrip.main.global.logging

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.user.domain.UserRole
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequestLoggingFilterTest {

    private val requestIdGenerator = RequestIdGenerator()
    private val filter = RequestLoggingFilter(requestIdGenerator)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    @Test
    fun `요청 ID가 없으면 새 요청 ID를 응답 헤더와 MDC에 저장한다`() {
        val request = MockHttpServletRequest("GET", "/api/trips")
        val response = MockHttpServletResponse()
        var requestIdInChain: String? = null

        filter.doFilter(request, response, FilterChain { _, _ ->
            requestIdInChain = MDC.get(LoggingMdcKeys.REQUEST_ID)
        })

        assertEquals(requestIdInChain, response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER))
        assertNull(MDC.get(LoggingMdcKeys.REQUEST_ID))
    }

    @Test
    fun `기존 요청 ID가 있으면 유지한다`() {
        val request = MockHttpServletRequest("GET", "/api/trips").apply {
            addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "request-123")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ ->
            assertEquals("request-123", MDC.get(LoggingMdcKeys.REQUEST_ID))
        })

        assertEquals("request-123", response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER))
    }

    @Test
    fun `인증 사용자는 사용자 ID를 MDC에 저장한다`() {
        val authUser = AuthUser(userId = 7L, role = UserRole.USER)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            authUser,
            null,
            authUser.getAuthorities(),
        )
        val request = MockHttpServletRequest("GET", "/api/users/me")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ ->
            assertEquals("7", MDC.get(LoggingMdcKeys.USER_ID))
        })

        assertNull(MDC.get(LoggingMdcKeys.USER_ID))
    }
}
