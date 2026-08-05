package com.togethertrip.main.auth.service.apple

import com.togethertrip.main.auth.client.AppleOAuthClient
import org.springframework.stereotype.Service

@Service
class AppleOAuthAccountRevocationService(
    private val appleOAuthClient: AppleOAuthClient,
    private val appleTokenCipher: AppleTokenCipher,
) : OAuthAccountRevoker {
    override fun revokeEncrypted(encryptedRefreshToken: String) {
        appleOAuthClient.revoke(appleTokenCipher.decrypt(encryptedRefreshToken))
    }
}
