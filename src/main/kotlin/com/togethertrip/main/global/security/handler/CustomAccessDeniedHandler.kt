package com.togethertrip.main.global.security.handler

import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class CustomAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()

        val errorCode = CommonErrorCode.ACCESS_DENIED
        val errorResponse = ErrorResponse(
            code = errorCode.code,
            message = errorCode.message,
        )

        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}
