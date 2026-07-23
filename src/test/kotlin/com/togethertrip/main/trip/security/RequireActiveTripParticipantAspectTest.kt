package com.togethertrip.main.trip.security

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.post.controller.PostController
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
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

class RequireActiveTripParticipantAspectTest {

    private val tripParticipantRepository = mock(TripParticipantRepository::class.java)
    private val aspect = RequireActiveTripParticipantAspect(tripParticipantRepository)

    @Test
    fun `활성 여행 참여자면 원래 메서드를 실행한다`() {
        val joinPoint = createJoinPoint(
            parameterNames = arrayOf("authUser", "tripId"),
            args = arrayOf(AuthUser(userId = 1L, role = UserRole.USER), 10L),
        )

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(createParticipant())
        `when`(joinPoint.proceed()).thenReturn("ok")

        val result = aspect.verify(joinPoint, annotation())

        assertEquals("ok", result)
        verify(joinPoint).proceed()
    }

    @Test
    fun `활성 여행 참여자가 아니면 실패한다`() {
        val joinPoint = createJoinPoint(
            parameterNames = arrayOf("authUser", "tripId"),
            args = arrayOf(AuthUser(userId = 1L, role = UserRole.USER), 10L),
        )

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(null)

        val exception = assertBusinessException {
            aspect.verify(joinPoint, annotation())
        }

        assertEquals(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `tripId 파라미터 이름을 명시해서 Long 인자를 구분한다`() {
        val joinPoint = createJoinPoint(
            parameterNames = arrayOf("authUser", "postId", "tripId"),
            args = arrayOf(AuthUser(userId = 1L, role = UserRole.USER), 300L, 10L),
        )

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(createParticipant())
        `when`(joinPoint.proceed()).thenReturn("ok")

        val result = aspect.verify(joinPoint, annotation())

        assertEquals("ok", result)
    }

    @Test
    fun `tripId 파라미터 이름을 찾을 수 없으면 잘못된 적용으로 실패한다`() {
        val joinPoint = createJoinPoint(
            parameterNames = arrayOf("authUser", "postId"),
            args = arrayOf(AuthUser(userId = 1L, role = UserRole.USER), 300L),
        )

        val exception = assertIllegalStateException {
            aspect.verify(joinPoint, annotation())
        }

        assertEquals("Cannot find trip id parameter 'tripId'.", exception.message)
    }

    @Test
    fun `PostController 게시글 댓글 API 전체에 활성 참가자 검증이 선언되어 있다`() {
        val protectedMethods = listOf(
            "createPost",
            "getPosts",
            "getPost",
            "updatePost",
            "deletePost",
            "createComment",
            "getComments",
            "deleteComment",
        )

        protectedMethods.forEach { methodName ->
            val method = PostController::class.java.methods.firstOrNull { it.name == methodName }
            assertNotNull(method, "$methodName method must exist.")
            assertNotNull(
                method.getAnnotation(RequireActiveTripParticipant::class.java),
                "$methodName must require active trip participant.",
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

    private fun annotation(): RequireActiveTripParticipant {
        return Fixture::class.java
            .getDeclaredMethod("method", AuthUser::class.java, Long::class.javaPrimitiveType)
            .getAnnotation(RequireActiveTripParticipant::class.java)
    }

    private fun createParticipant(): TripParticipant {
        val user = User(nickname = "재완").apply {
            id = 1L
        }
        val trip = Trip(
            ownerUser = user,
            title = "일본 여행",
            defaultCurrency = "JPY",
        ).apply {
            id = 10L
        }

        return TripParticipant(
            trip = trip,
            user = user,
            displayName = "재완",
            participantRole = TripParticipantRole.MEMBER,
            participantStatus = TripParticipantStatus.ACTIVE,
        )
    }

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try {
            block()
            throw AssertionError("BusinessException이 발생해야 합니다.")
        } catch (exception: BusinessException) {
            exception
        }
    }

    private fun assertIllegalStateException(block: () -> Unit): IllegalStateException {
        return try {
            block()
            throw AssertionError("IllegalStateException이 발생해야 합니다.")
        } catch (exception: IllegalStateException) {
            exception
        }
    }

    private class Fixture {
        @RequireActiveTripParticipant
        fun method(
            authUser: AuthUser,
            tripId: Long,
        ) {
            authUser.toString()
            tripId.toString()
        }
    }
}
