package com.togethertrip.main.global.exception

import com.togethertrip.main.global.response.ErrorResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.MethodArgumentNotValidException
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
                    code = errorCode.code,
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

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(
        exception: MethodArgumentNotValidException,
    ): ResponseEntity<ErrorResponse> {
        val message = exception.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { CommonErrorCode.INVALID_INPUT.message }

        return ResponseEntity
            .status(CommonErrorCode.INVALID_INPUT.status)
            .body(
                ErrorResponse(
                    code = CommonErrorCode.INVALID_INPUT.code,
                    message = message,
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
                    code = CommonErrorCode.INTERNAL_SERVER_ERROR.code,
                    message = CommonErrorCode.INTERNAL_SERVER_ERROR.message,
                )
            )
    }
}
