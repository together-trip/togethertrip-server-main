package com.togethertrip.main.auth.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.user.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "oauth_accounts")
class OAuthAccount(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var provider: OAuthProvider,

    @Column(name = "provider_user_id", nullable = false, length = 100)
    var providerUserId: String,

    @Column(nullable = true, length = 50)
    var nickname: String? = null,

    @Column(name = "profile_image_url", nullable = true, length = 500)
    var profileImageUrl: String? = null,

    @Column(name = "encrypted_refresh_token", nullable = true, length = 2000)
    var encryptedRefreshToken: String? = null,

) : BaseEntity()
