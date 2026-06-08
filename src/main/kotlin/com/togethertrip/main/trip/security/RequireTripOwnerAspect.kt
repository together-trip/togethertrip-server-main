package com.togethertrip.main.trip.security

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripRepository
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component

@Aspect
@Component
class RequireTripOwnerAspect(
    private val tripRepository: TripRepository,
) {

    @Around("@annotation(requireTripOwner)")
    fun verify(
        joinPoint: ProceedingJoinPoint,
        requireTripOwner: RequireTripOwner,
    ): Any? {
        val authUser = joinPoint.args.filterIsInstance<AuthUser>().firstOrNull()
            ?: throw IllegalStateException("@RequireTripOwner requires an AuthUser argument.")
        val tripId = findTripId(joinPoint, requireTripOwner.tripIdParam)
        val trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
            ?: throw BusinessException(TripErrorCode.TRIP_NOT_FOUND)

        if (trip.ownerUser.id != authUser.userId) {
            throw BusinessException(TripErrorCode.TRIP_OWNER_ONLY)
        }

        return joinPoint.proceed()
    }

    private fun findTripId(
        joinPoint: ProceedingJoinPoint,
        tripIdParam: String,
    ): Long {
        val signature = joinPoint.signature as? MethodSignature
            ?: throw IllegalStateException("@RequireTripOwner requires a method signature.")
        val parameterNames = signature.parameterNames
            ?: throw IllegalStateException("@RequireTripOwner requires method parameter names.")
        val parameterIndex = parameterNames.indexOf(tripIdParam)

        if (parameterIndex < 0) {
            throw IllegalStateException("Cannot find trip id parameter '$tripIdParam'.")
        }

        return joinPoint.args.getOrNull(parameterIndex) as? Long
            ?: throw IllegalStateException("Parameter '$tripIdParam' must be Long.")
    }
}
