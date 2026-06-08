package com.togethertrip.main.trip.security

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireActiveTripParticipant(
    val tripIdParam: String = "tripId",
)
