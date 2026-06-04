package com.togethertrip.main.auth.service.phone

import com.togethertrip.main.auth.service.oauth.OAuthTemporarySession

data class ConfirmedPhoneVerification(
    val session: OAuthTemporarySession,
    val phoneNumber: String,
)
