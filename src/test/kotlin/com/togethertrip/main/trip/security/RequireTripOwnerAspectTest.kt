package com.togethertrip.main.trip.security

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.settlement.controller.SettlementController
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserRole
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RequireTripOwnerAspectTest {

    private val tripRepository = mock(TripRepository::class.java)
    private val aspect = RequireTripOwnerAspect(tripRepository)

    @Test
    fun `여행 방장이면 원래 메서드를 실행한다`() {
        val joinPoint = createJoinPoint(
            parameterNames = arrayOf("authUser", "tripId"),
            args = arrayOf(AuthUser(userId = 1L, role = UserRole.USER), 10L),
        )

        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(createTrip(ownerUserId = 1L))
        `when`(joinPoint.proceed()).thenReturn("ok")

        val result = aspect.verify(joinPoint, annotation())

        assertEquals("ok", result)
        verify(joinPoint).proceed()
    }

    @Test
    fun `여행 방장이 아니면 실패한다`() {
        val joinPoint = createJoinPoint(
            parameterNames = arrayOf("authUser", "tripId"),
            args = arrayOf(AuthUser(userId = 2L, role = UserRole.USER), 10L),
        )

        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(createTrip(ownerUserId = 1L))

        val exception = assertBusinessException {
            aspect.verify(joinPoint, annotation())
        }

        assertEquals(TripErrorCode.TRIP_OWNER_ONLY, exception.errorCode)
    }

    @Test
    fun `정산 확정과 공유 토큰 생성은 방장 권한을 요구한다`() {
        val protectedMethods = listOf(
            "confirmSettlement",
            "createShareToken",
        )

        protectedMethods.forEach { methodName ->
            val method = SettlementController::class.java.methods.firstOrNull { it.name == methodName }
            assertNotNull(method, "$methodName method must exist.")
            assertNotNull(
                method.getAnnotation(RequireTripOwner::class.java),
                "$methodName must require trip owner.",
            )
        }
    }

    private fun createJoinPoint(
        parameterNames: Array<String>,
        args: Array<Any>,
    ): ProceedingJoinPoint {
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.args).thenReturn(args)
        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.parameterNames).thenReturn(parameterNames)

        return joinPoint
    }

    private fun annotation(): RequireTripOwner {
        return Fixture::class.java
            .getDeclaredMethod("method", AuthUser::class.java, Long::class.javaPrimitiveType)
            .getAnnotation(RequireTripOwner::class.java)
    }

    private fun createTrip(ownerUserId: Long): Trip {
        val owner = User(nickname = "방장").apply {
            id = ownerUserId
        }

        return Trip(
            ownerUser = owner,
            title = "일본 여행",
            defaultCurrency = "JPY",
        ).apply {
            id = 10L
        }
    }

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try {
            block()
            throw AssertionError("BusinessException이 발생해야 합니다.")
        } catch (exception: BusinessException) {
            exception
        }
    }

    private class Fixture {
        @RequireTripOwner
        fun method(
            authUser: AuthUser,
            tripId: Long,
        ) {
            authUser.toString()
            tripId.toString()
        }
    }
}
