package com.togethertrip.main.trip.security

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component

@Aspect
@Component
class RequireActiveTripParticipantAspect(
    private val tripParticipantRepository: TripParticipantRepository,
) {

    @Around("@annotation(requireActiveTripParticipant)")
    fun verify(
        joinPoint: ProceedingJoinPoint,
        requireActiveTripParticipant: RequireActiveTripParticipant,
    ): Any? {
        val authUser = joinPoint.args.filterIsInstance<AuthUser>().firstOrNull()
            ?: throw IllegalStateException("@RequireActiveTripParticipant requires an AuthUser argument.")
        val tripId = findTripId(joinPoint, requireActiveTripParticipant.tripIdParam)

        tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
            tripId = tripId,
            userId = authUser.userId,
            participantStatus = TripParticipantStatus.ACTIVE,
        ) ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)

        return joinPoint.proceed()
    }

    private fun findTripId(
        joinPoint: ProceedingJoinPoint,
        tripIdParam: String,
    ): Long {
        val signature = joinPoint.signature as? MethodSignature
            ?: throw IllegalStateException("@RequireActiveTripParticipant requires a method signature.")
        val parameterNames = signature.parameterNames
            ?: throw IllegalStateException("@RequireActiveTripParticipant requires method parameter names.")
        val parameterIndex = parameterNames.indexOf(tripIdParam)

        if (parameterIndex < 0) {
            throw IllegalStateException("Cannot find trip id parameter '$tripIdParam'.")
        }

        return joinPoint.args.getOrNull(parameterIndex) as? Long
            ?: throw IllegalStateException("Parameter '$tripIdParam' must be Long.")
    }
}
