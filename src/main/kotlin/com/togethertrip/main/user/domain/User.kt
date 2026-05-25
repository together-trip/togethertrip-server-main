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

    @Column(nullable = true, length = 255)
    var email: String? = null,

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

) : BaseEntity()
