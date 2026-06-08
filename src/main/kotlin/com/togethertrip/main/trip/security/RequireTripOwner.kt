package com.togethertrip.main.trip.security

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireTripOwner(
    val tripIdParam: String = "tripId",
)
