package com.togethertrip.main.auth.service.phone

import java.time.Instant

data class PhoneVerificationState(
    val phoneNumber: String,
    val code: String,
    val expiresAt: Instant,
    val attemptCount: Int = 0,
)
