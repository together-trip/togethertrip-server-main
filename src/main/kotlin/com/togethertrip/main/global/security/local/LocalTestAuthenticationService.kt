package com.togethertrip.main.global.security.local

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserRole
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LocalTestAuthenticationService(
    private val userRepository: UserRepository,
    @Value("\${auth.local-test.enabled}")
    private val enabled: Boolean,
) {

    @Transactional
    fun authenticate(token: String): AuthUser? {
        if (!enabled || !token.startsWith(TOKEN_PREFIX)) {
            return null
        }

        val tokenValue = token.removePrefix(TOKEN_PREFIX)
        if (tokenValue == "admin") {
            return getOrCreateAuthUser("로컬 admin", UserRole.ADMIN)
        }

        val nickname = legacyNickname(tokenValue) ?: tokenValue
            .takeIf { it.isNotBlank() }
            ?.let { "로컬 $it" }
            ?: return null
        return getOrCreateAuthUser(nickname, UserRole.USER)
    }

    private fun legacyNickname(tokenValue: String): String? {
        val parts = tokenValue.split(":", limit = 2)
        if (parts.size != 2 || parts[0] !in LEGACY_LABELS || parts[1].isBlank()) {
            return null
        }
        return "로컬 ${parts[0]} ${parts[1]}"
    }

    private fun getOrCreateAuthUser(
        nickname: String,
        role: UserRole,
    ): AuthUser {
        val user = userRepository.findByNicknameAndDeletedAtIsNull(nickname)
            ?: userRepository.save(User(nickname = nickname, role = role))
        if (user.role != role) {
            user.role = role
        }
        return AuthUser(userId = user.id, role = user.role)
    }

    companion object {
        private const val TOKEN_PREFIX = "local-test:"
        private val LEGACY_LABELS = setOf("verified", "unverified")
    }
}
