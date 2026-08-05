package com.togethertrip.main.auth.service.apple

fun interface OAuthAccountRevoker {
    fun revokeEncrypted(encryptedRefreshToken: String)

    object NoOp : OAuthAccountRevoker {
        override fun revokeEncrypted(encryptedRefreshToken: String) = Unit
    }
}
