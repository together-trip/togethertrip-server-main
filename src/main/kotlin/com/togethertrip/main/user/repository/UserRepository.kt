package com.togethertrip.main.user.repository

import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): User?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    fun findLockedByIdAndDeletedAtIsNull(@Param("id") id: Long): User?

    fun findByNicknameAndDeletedAtIsNull(nickname: String): User?

    fun existsByNicknameAndDeletedAtIsNull(nickname: String): Boolean

    fun existsByNicknameAndIdNotAndDeletedAtIsNull(
        nickname: String,
        id: Long,
    ): Boolean

    fun existsByPhoneNumberAndDeletedAtIsNull(phoneNumber: String): Boolean

    fun existsByPhoneNumberAndIdNotAndDeletedAtIsNull(
        phoneNumber: String,
        id: Long,
    ): Boolean

    fun findByPhoneNumberAndPhoneVerifiedAtIsNotNullAndStatusAndDeletedAtIsNull(
        phoneNumber: String,
        status: UserStatus,
    ): User?
}
