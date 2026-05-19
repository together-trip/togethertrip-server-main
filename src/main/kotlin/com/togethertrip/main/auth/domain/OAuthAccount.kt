package com.togethertrip.main.auth.domain

import com.togethertrip.main.user.domain.User
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "oauth_accounts",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_oauth_accounts_provider_provider_user_id",
            columnNames = ["provider", "provider_user_id"]
        )
    ]
)
class OAuthAccount(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var provider: OAuthProvider,

    @Column(name = "provider_user_id", nullable = false, length = 100)
    var providerUserId: String,

    @Column(nullable = true, length = 255)
    var email: String? = null,

    @Column(nullable = true, length = 50)
    var nickname: String? = null,

    @Column(name = "profile_image_url", nullable = true, length = 500)
    var profileImageUrl: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0L,
)