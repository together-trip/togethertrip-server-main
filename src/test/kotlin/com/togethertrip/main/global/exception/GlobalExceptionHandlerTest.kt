package com.togethertrip.main.global.exception

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.HttpMethod
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.servlet.resource.NoResourceFoundException
import kotlin.test.assertEquals

class GlobalExceptionHandlerTest {

    @Test
    fun `optimistic lock 충돌은 409로 응답한다`() {
        val handler = GlobalExceptionHandler()

        val response = handler.handleObjectOptimisticLockingFailureException(
            ObjectOptimisticLockingFailureException("Trip", 10L)
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(CommonErrorCode.CONCURRENT_MODIFICATION.code, response.body?.code)
    }

    @Test
    fun `정적 리소스가 없으면 에러 로그 대상이 아닌 404로 응답한다`() {
        val handler = GlobalExceptionHandler()

        val response = handler.handleNoResourceFoundException(
            NoResourceFoundException(
                HttpMethod.GET,
                "/uploads/user-profile-images/missing.jpg",
                "missing.jpg",
            )
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(null, response.body)
    }
}
