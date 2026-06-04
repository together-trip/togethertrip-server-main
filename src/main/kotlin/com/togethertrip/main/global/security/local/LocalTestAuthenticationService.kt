package com.togethertrip.main.global.security.local

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserRole
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.abs

@Service
class LocalTestAuthenticationService(
    private val userRepository: UserRepository,
    @Value("\${auth.local-test.enabled:false}")
    private val enabled: Boolean,
) {

    @Transactional
    fun authenticate(token: String): AuthUser? {
        if (!enabled || !token.startsWith(TOKEN_PREFIX)) {
            return null
        }

        val parts = token
            .removePrefix(TOKEN_PREFIX)
            .split(":")

        val phoneVerified = when (parts.firstOrNull()) {
            "verified" -> true
            "unverified" -> false
            else -> return null
        }
        val identifier = parts.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: "swagger"
        val user = getOrCreateLocalUser(
            identifier = identifier,
            phoneVerified = phoneVerified,
        )

        return AuthUser(
            userId = user.id,
            role = user.role,
        )
    }

    private fun getOrCreateLocalUser(
        identifier: String,
        phoneVerified: Boolean,
    ): User {
        val verificationLabel = if (phoneVerified) "verified" else "unverified"
        val email = "local-test-$verificationLabel-$identifier@togethertrip.local"
        val user = userRepository.findByEmailAndDeletedAtIsNull(email)
            ?: userRepository.save(
                User(
                    email = email,
                    nickname = "로컬 $verificationLabel $identifier",
                    role = UserRole.USER,
                )
            )

        if (phoneVerified && user.phoneVerifiedAt == null) {
            user.verifyPhoneNumber(createPhoneNumber(email))
        }

        return user
    }

    private fun createPhoneNumber(seed: String): String {
        val suffix = seed
            .hashCode()
            .let { abs(it.toLong()) }
            .rem(100_000_000)
            .toString()
            .padStart(8, '0')

        return "+8210$suffix"
    }

    companion object {
        private const val TOKEN_PREFIX = "local-test:"
    }
}
