package com.togethertrip.main.user.repository

import com.togethertrip.main.user.domain.UserAgreement
import org.springframework.data.jpa.repository.JpaRepository

interface UserAgreementRepository : JpaRepository<UserAgreement, Long>
