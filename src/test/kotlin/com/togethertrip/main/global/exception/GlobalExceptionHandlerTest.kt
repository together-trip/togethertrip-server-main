package com.togethertrip.main.global.exception

import org.junit.jupiter.api.Test
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.http.HttpStatus
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
}
