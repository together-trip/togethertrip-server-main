package com.togethertrip.main.user.repository

import com.togethertrip.main.user.domain.UserAgreement
import com.togethertrip.main.user.domain.UserAgreementType
import org.springframework.data.jpa.repository.JpaRepository

interface UserAgreementRepository : JpaRepository<UserAgreement, Long> {
    fun findByUserIdAndAgreementTypeAndDeletedAtIsNull(
        userId: Long,
        agreementType: UserAgreementType,
    ): UserAgreement?

    fun findAllByUserIdAndDeletedAtIsNull(userId: Long): List<UserAgreement>

    fun deleteAllByUserId(userId: Long)
}
