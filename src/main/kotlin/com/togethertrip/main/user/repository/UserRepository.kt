package com.togethertrip.main.user.repository

import com.togethertrip.main.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
}