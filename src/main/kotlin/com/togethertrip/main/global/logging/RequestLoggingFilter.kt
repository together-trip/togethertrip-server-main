package com.togethertrip.main.global.logging

import com.togethertrip.main.global.security.principal.AuthUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class RequestLoggingFilter(
    private val requestIdGenerator: RequestIdGenerator,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RequestLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = System.nanoTime()
        val requestId = resolveRequestId(request)

        MDC.put(LoggingMdcKeys.REQUEST_ID, requestId)
        MDC.put(LoggingMdcKeys.METHOD, request.method)
        MDC.put(LoggingMdcKeys.PATH, request.requestURI)
        response.setHeader(REQUEST_ID_HEADER, requestId)

        var failure: Throwable? = null

        try {
            updateUserId()
            filterChain.doFilter(request, response)
        } catch (exception: Throwable) {
            failure = exception
            throw exception
        } finally {
            updateUserId()
            logRequest(request, response, elapsedMillis(startedAt), failure)
            MDC.clear()
        }
    }

    private fun resolveRequestId(request: HttpServletRequest): String {
        val requestId = request.getHeader(REQUEST_ID_HEADER)
            ?.takeIf { it.isNotBlank() }
            ?.take(MAX_REQUEST_ID_LENGTH)

        return requestId ?: requestIdGenerator.generate()
    }

    private fun updateUserId() {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        val userId = when (principal) {
            is AuthUser -> principal.userId.toString()
            else -> ANONYMOUS_USER
        }

        MDC.put(LoggingMdcKeys.USER_ID, userId)
    }

    private fun logRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
        elapsedMs: Long,
        failure: Throwable?,
    ) {
        val queryString = request.queryString
            ?.let(SensitiveDataMasker::mask)
            ?.let { "?$it" }
            ?: ""
        val path = "${request.requestURI}$queryString"

        if (failure == null) {
            log.info(
                "http request completed method={} path={} status={} elapsedMs={}",
                request.method,
                path,
                response.status,
                elapsedMs,
            )
        } else {
            log.error(
                "http request failed method={} path={} status={} elapsedMs={} exception={}",
                request.method,
                path,
                response.status,
                elapsedMs,
                failure::class.simpleName,
                failure,
            )
        }
    }

    private fun elapsedMillis(startedAt: Long): Long {
        return (System.nanoTime() - startedAt) / 1_000_000
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        private const val MAX_REQUEST_ID_LENGTH = 100
        private const val ANONYMOUS_USER = "anonymous"
    }
}
