package com.togethertrip.main.auth.service.phone

import java.time.Instant

data class PhoneVerificationState(
    val phoneNumberHash: String,
    val code: String,
    val expiresAt: Instant,
)
