package com.togethertrip.main.global.exception

import com.togethertrip.main.global.response.ErrorResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        exception: BusinessException,
    ): ResponseEntity<ErrorResponse> {
        val errorCode = exception.errorCode

        return ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse(
                    code = errorCode.name,
                    message = errorCode.message,
                )
            )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        exception: IllegalArgumentException,
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .badRequest()
            .body(
                ErrorResponse(
                    code = "BAD_REQUEST",
                    message = exception.message ?: "잘못된 요청입니다.",
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleException(
        exception: Exception,
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .internalServerError()
            .body(
                ErrorResponse(
                    code = ErrorCode.INTERNAL_SERVER_ERROR.name,
                    message = ErrorCode.INTERNAL_SERVER_ERROR.message,
                )
            )
    }
}