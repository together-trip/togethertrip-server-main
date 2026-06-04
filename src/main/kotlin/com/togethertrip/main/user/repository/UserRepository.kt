package com.togethertrip.main.user.repository

import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): User?

    fun findByEmailAndDeletedAtIsNull(email: String): User?

    fun existsByPhoneNumberAndDeletedAtIsNull(phoneNumber: String): Boolean

    fun findByPhoneNumberAndPhoneVerifiedAtIsNotNullAndStatusAndDeletedAtIsNull(
        phoneNumber: String,
        status: UserStatus,
    ): User?
}
