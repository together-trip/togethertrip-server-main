package com.togethertrip.main.global.logging

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals

class ServiceLoggingAspectTest {

    private val aspect = ServiceLoggingAspect()

    @Test
    fun `서비스 메서드 결과를 그대로 반환한다`() {
        val joinPoint = mockJoinPoint(result = "ok")

        val result = aspect.logServiceExecution(joinPoint)

        assertEquals("ok", result)
        verify(joinPoint).proceed()
    }

    @Test
    fun `서비스 메서드 예외를 다시 던진다`() {
        val exception = IllegalStateException("token=secret")
        val joinPoint = mockJoinPoint(exception = exception)

        val thrown = try {
            aspect.logServiceExecution(joinPoint)
            throw AssertionError("예외가 다시 던져져야 합니다.")
        } catch (thrown: IllegalStateException) {
            thrown
        }

        assertEquals(exception, thrown)
        verify(joinPoint).proceed()
    }

    private fun mockJoinPoint(
        result: Any? = null,
        exception: Throwable? = null,
    ): ProceedingJoinPoint {
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(signature.declaringType).thenReturn(ServiceLoggingAspectTest::class.java)
        `when`(signature.name).thenReturn("sample")
        `when`(joinPoint.signature).thenReturn(signature)
        `when`(joinPoint.args).thenReturn(arrayOf("accessToken=secret"))

        if (exception == null) {
            `when`(joinPoint.proceed()).thenReturn(result)
        } else {
            `when`(joinPoint.proceed()).thenThrow(exception)
        }

        return joinPoint
    }
}
