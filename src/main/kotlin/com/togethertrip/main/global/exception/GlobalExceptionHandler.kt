package com.togethertrip.main.global.exception

import com.togethertrip.main.global.response.ErrorResponse
import jakarta.persistence.OptimisticLockException
import org.hibernate.StaleObjectStateException
import org.slf4j.LoggerFactory
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.TransactionSystemException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        exception: BusinessException,
    ): ResponseEntity<ErrorResponse> {
        val errorCode = exception.errorCode
        logger.warn(
            "business exception occurred code={} message={}",
            errorCode.code,
            errorCode.message,
        )

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
        logger.warn("illegal argument exception occurred message={}", exception.message)

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
        logger.warn("validation exception occurred message={}", message)

        return ResponseEntity
            .status(CommonErrorCode.INVALID_INPUT.status)
            .body(
                ErrorResponse(
                    code = CommonErrorCode.INVALID_INPUT.code,
                    message = message,
                )
            )
    }

    @ExceptionHandler(
        ObjectOptimisticLockingFailureException::class,
        OptimisticLockingFailureException::class,
        OptimisticLockException::class,
        StaleObjectStateException::class,
        CannotAcquireLockException::class,
        PessimisticLockingFailureException::class,
    )
    fun handleConcurrentModificationException(
        exception: Exception,
    ): ResponseEntity<ErrorResponse> {
        logger.warn(
            "concurrent modification exception occurred exception={} message={}",
            exception::class.simpleName,
            exception.message,
        )

        return concurrentModificationResponse()
    }

    @ExceptionHandler(TransactionSystemException::class)
    fun handleTransactionSystemException(
        exception: TransactionSystemException,
    ): ResponseEntity<ErrorResponse> {
        if (exception.hasConcurrentModificationCause()) {
            logger.warn(
                "transaction system exception caused by concurrent modification message={}",
                exception.message,
            )

            return concurrentModificationResponse()
        }

        return handleException(exception)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolationException(
        exception: DataIntegrityViolationException,
    ): ResponseEntity<ErrorResponse> {
        if (exception.hasTransactionEventVersionConflictCause()) {
            logger.warn(
                "transaction event version conflict occurred message={}",
                exception.message,
            )

            return concurrentModificationResponse()
        }

        return handleException(exception)
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(
        exception: NoResourceFoundException,
    ): ResponseEntity<ErrorResponse> {
        logger.debug(
            "static resource not found path={}",
            exception.resourcePath,
        )

        return ResponseEntity
            .notFound()
            .build()
    }

    @ExceptionHandler(Exception::class)
    fun handleException(
        exception: Exception,
    ): ResponseEntity<ErrorResponse> {
        logger.error(
            "unexpected exception occurred exception={} message={}",
            exception::class.simpleName,
            exception.message,
            exception,
        )

        return ResponseEntity
            .internalServerError()
            .body(
                ErrorResponse(
                    code = CommonErrorCode.INTERNAL_SERVER_ERROR.code,
                    message = CommonErrorCode.INTERNAL_SERVER_ERROR.message,
                )
            )
    }

    private fun concurrentModificationResponse(): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(CommonErrorCode.CONCURRENT_MODIFICATION.status)
            .body(
                ErrorResponse(
                    code = CommonErrorCode.CONCURRENT_MODIFICATION.code,
                    message = CommonErrorCode.CONCURRENT_MODIFICATION.message,
                )
            )
    }

    private fun Throwable.hasConcurrentModificationCause(): Boolean {
        return generateSequence(this) { throwable -> throwable.cause }
            .any { throwable -> throwable.isConcurrentModificationException() }
    }

    private fun Throwable.isConcurrentModificationException(): Boolean {
        return this is ObjectOptimisticLockingFailureException ||
            this is OptimisticLockingFailureException ||
            this is OptimisticLockException ||
            this is StaleObjectStateException ||
            this is CannotAcquireLockException ||
            this is PessimisticLockingFailureException
    }

    private fun Throwable.hasTransactionEventVersionConflictCause(): Boolean {
        return generateSequence(this) { throwable -> throwable.cause }
            .mapNotNull { throwable -> throwable.message }
            .any { message -> message.contains(TRANSACTION_EVENT_VERSION_CONSTRAINT) }
    }

    private companion object {
        private const val TRANSACTION_EVENT_VERSION_CONSTRAINT = "uk_transaction_events_version"
    }
}
