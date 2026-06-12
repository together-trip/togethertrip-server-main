package com.togethertrip.main.auth.service.phone

import com.togethertrip.main.auth.service.oauth.OAuthTemporarySession

data class ConfirmedPhoneVerification(
    val session: OAuthTemporarySession,
    val phoneNumberHash: String,
    val phoneNumberHashVersion: String,
    val phoneNumberEncrypted: String? = null,
    val phoneNumberEncryptionVersion: String? = null,
    val phoneNumberMasked: String? = null,
)
