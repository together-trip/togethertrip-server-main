package com.togethertrip.main.user.domain

import com.togethertrip.main.global.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
class User(

    @Column(nullable = false, length = 50)
    var nickname: String,

    @Column(length = 20)
    var gender: String? = null,

    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,

    @Column(name = "profile_image_url", nullable = true, length = 500)
    var profileImageUrl: String? = null,

    @Column(name = "phone_number", length = 30)
    var phoneNumber: String? = null,

    @Column(name = "phone_verified_at")
    var phoneVerifiedAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: UserRole = UserRole.USER,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,

) : BaseEntity() {

    fun updateProfile(
        nickname: String?,
        gender: String?,
        birthDate: LocalDate?,
        profileImageUrl: String?,
    ) {
        if (nickname != null) {
            this.nickname = nickname
        }

        if (gender != null) {
            this.gender = gender
        }

        if (birthDate != null) {
            this.birthDate = birthDate
        }

        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl
        }

        updatedAt = Instant.now()
    }

    fun withdraw(now: Instant = Instant.now()) {
        status = UserStatus.WITHDRAWN
        markDeleted(now)
    }

    fun verifyPhoneNumber(
        phoneNumber: String,
        verifiedAt: Instant = Instant.now(),
    ) {
        this.phoneNumber = phoneNumber
        phoneVerifiedAt = verifiedAt
        updatedAt = verifiedAt
    }

    fun isProfileCompleted(): Boolean {
        return nickname.isNotBlank() && gender != null && birthDate != null
    }
}
