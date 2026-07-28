package com.togethertrip.main.user.domain

import com.togethertrip.main.global.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "users")
class User(

    @Column(nullable = false, length = 50)
    var nickname: String,

    @Column(length = 20)
    var gender: String? = null,

    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,

    @Column(name = "profile_image_url", nullable = true, length = 500)
    var profileImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: UserRole = UserRole.USER,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(name = "moderation_restricted_at")
    var moderationRestrictedAt: Instant? = null,

    @Column(name = "moderation_restricted_until")
    var moderationRestrictedUntil: Instant? = null,

    @Column(name = "moderation_restriction_reason", length = 500)
    var moderationRestrictionReason: String? = null,

) : BaseEntity() {

    fun updateProfile(
        nickname: String?,
        gender: String?,
        birthDate: LocalDate?,
        profileImageUrl: String?,
    ) {
        // 닉네임 변경
        if (nickname != null) {
            this.nickname = nickname
        }

        // 성별 변경
        if (gender != null) {
            this.gender = gender
        }

        // 생년월일 변경
        if (birthDate != null) {
            this.birthDate = birthDate
        }

        // 프로필 이미지 변경
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl
        }

        // 수정 시각 갱신
        updatedAt = Instant.now()
    }

    fun anonymizeAndWithdraw(now: Instant = Instant.now()) {
        nickname = WITHDRAWN_USER_NICKNAME
        gender = null
        birthDate = null
        profileImageUrl = null
        moderationRestrictedAt = null
        moderationRestrictedUntil = null
        moderationRestrictionReason = null
        status = UserStatus.WITHDRAWN
        markDeleted(now)
    }

    fun isProfileCompleted(): Boolean {
        return nickname.isNotBlank()
    }

    fun restrictModeration(reason: String?, until: Instant?, now: Instant) {
        moderationRestrictedAt = now
        moderationRestrictedUntil = until
        moderationRestrictionReason = reason?.take(500)
        updatedAt = now
    }

    fun clearModerationRestriction(now: Instant) {
        moderationRestrictedAt = null
        moderationRestrictedUntil = null
        moderationRestrictionReason = null
        updatedAt = now
    }

    fun isModerationRestricted(now: Instant): Boolean {
        val restrictedAt = moderationRestrictedAt ?: return false
        val until = moderationRestrictedUntil
        return restrictedAt <= now && (until == null || until > now)
    }

    companion object {
        const val WITHDRAWN_USER_NICKNAME = "탈퇴한 사용자"
    }
}
