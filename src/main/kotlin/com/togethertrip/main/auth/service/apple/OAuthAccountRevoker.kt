package com.togethertrip.main.auth.service.apple

interface OAuthAccountRevoker {
    fun revokeForUser(userId: Long)

    object NoOp : OAuthAccountRevoker {
        override fun revokeForUser(userId: Long) = Unit
    }
}
