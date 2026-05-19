package com.togethertrip.main.global.security.principal

import com.togethertrip.main.user.domain.UserRole
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

data class AuthUser(
    val userId: Long,
    val role: UserRole,
) {

    fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
    }
}