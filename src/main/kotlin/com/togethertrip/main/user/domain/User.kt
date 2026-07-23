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

    @Column(name = "phone_number", length = 30)
    var phoneNumber: String? = null,

    @Column(name = "phone_number_encrypted", columnDefinition = "TEXT")
    var phoneNumberEncrypted: String? = null,

    @Column(name = "phone_number_encryption_version", length = 30)
    var phoneNumberEncryptionVersion: String? = null,

    @Column(name = "phone_number_masked", length = 30)
    var phoneNumberMasked: String? = null,

    @Column(name = "phone_number_hash", length = 64)
    var phoneNumberHash: String? = null,

    @Column(name = "phone_number_hash_version", length = 30)
    var phoneNumberHashVersion: String? = null,

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

    fun withdraw(now: Instant = Instant.now()) {
        // 탈퇴 상태 변경
        status = UserStatus.WITHDRAWN
        markDeleted(now)
    }

    fun reactivateForSignup(now: Instant = Instant.now()) {
        // 재가입 상태 초기화
        status = UserStatus.ACTIVE
        deletedAt = null
        phoneNumber = null
        phoneNumberEncrypted = null
        phoneNumberEncryptionVersion = null
        phoneNumberMasked = null
        phoneNumberHash = null
        phoneNumberHashVersion = null
        phoneVerifiedAt = null
        updatedAt = now
    }

    fun verifyPhoneNumberHash(
        phoneNumberHash: String,
        phoneNumberHashVersion: String,
        phoneNumberEncrypted: String? = null,
        phoneNumberEncryptionVersion: String? = null,
        phoneNumberMasked: String? = null,
        verifiedAt: Instant = Instant.now(),
    ) {
        // 전화번호 인증 정보 저장
        this.phoneNumber = null
        this.phoneNumberEncrypted = phoneNumberEncrypted
        this.phoneNumberEncryptionVersion = phoneNumberEncryptionVersion
        this.phoneNumberMasked = phoneNumberMasked
        this.phoneNumberHash = phoneNumberHash
        this.phoneNumberHashVersion = phoneNumberHashVersion

        // 전화번호 인증 시각 갱신
        phoneVerifiedAt = verifiedAt
        updatedAt = verifiedAt
    }

    fun isProfileCompleted(): Boolean {
        return nickname.isNotBlank() && gender != null && birthDate != null
    }
}
