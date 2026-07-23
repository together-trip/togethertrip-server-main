package com.togethertrip.main.global.security.local

import com.togethertrip.main.global.phone.PhoneNumberCrypto
import com.togethertrip.main.global.phone.PhoneNumberHasher
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
    private val phoneNumberHasher: PhoneNumberHasher,
    private val phoneNumberCrypto: PhoneNumberCrypto,
    @Value("\${auth.local-test.enabled}")
    private val enabled: Boolean,
) {

    @Transactional
    fun authenticate(token: String): AuthUser? {
        // local-test token 사용 가능 여부 확인
        if (!enabled || !token.startsWith(TOKEN_PREFIX)) {
            return null
        }

        // local-test token 파싱
        val parts = token
            .removePrefix(TOKEN_PREFIX)
            .split(":")

        // local admin 사용자 인증
        if (parts.firstOrNull() == "admin") {
            val user = getOrCreateAdminUser()
            return AuthUser(
                userId = user.id,
                role = user.role,
            )
        }

        // 전화번호 인증 상태 파싱
        val phoneVerified = when (parts.firstOrNull()) {
            "verified" -> true
            "unverified" -> false
            else -> return null
        }

        // local 사용자 식별자 파싱
        val identifier = parts.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: "swagger"

        // local 사용자 조회 또는 생성
        val user = getOrCreateLocalUser(
            identifier = identifier,
            phoneVerified = phoneVerified,
        )

        // 인증 사용자 반환
        return AuthUser(
            userId = user.id,
            role = user.role,
        )
    }

    private fun getOrCreateLocalUser(
        identifier: String,
        phoneVerified: Boolean,
    ): User {
        // local 사용자 닉네임 생성
        val verificationLabel = if (phoneVerified) "verified" else "unverified"
        val nickname = "로컬 $verificationLabel $identifier"

        // local 사용자 조회 또는 저장
        val user = userRepository.findByNicknameAndDeletedAtIsNull(nickname)
            ?: userRepository.save(
                User(
                    nickname = nickname,
                    role = UserRole.USER,
                )
            )

        // local 사용자 전화번호 인증 처리
        if (phoneVerified && user.phoneVerifiedAt == null) {
            val phoneNumber = createPhoneNumber(nickname)
            user.verifyPhoneNumberHash(
                phoneNumberHash = phoneNumberHasher.hash(phoneNumber),
                phoneNumberHashVersion = phoneNumberHasher.version,
                phoneNumberEncrypted = phoneNumberCrypto.encrypt(phoneNumber),
                phoneNumberEncryptionVersion = phoneNumberCrypto.version,
                phoneNumberMasked = phoneNumberCrypto.mask(phoneNumber),
            )
        }

        return user
    }

    private fun getOrCreateAdminUser(): User {
        val nickname = "로컬 admin"
        val user = userRepository.findByNicknameAndDeletedAtIsNull(nickname)
            ?: userRepository.save(
                User(
                    nickname = nickname,
                    role = UserRole.ADMIN,
                )
            )

        // local admin 권한 보정
        if (user.role != UserRole.ADMIN) {
            user.role = UserRole.ADMIN
        }

        if (user.phoneVerifiedAt == null) {
            val phoneNumber = createPhoneNumber(nickname)
            user.verifyPhoneNumberHash(
                phoneNumberHash = phoneNumberHasher.hash(phoneNumber),
                phoneNumberHashVersion = phoneNumberHasher.version,
                phoneNumberEncrypted = phoneNumberCrypto.encrypt(phoneNumber),
                phoneNumberEncryptionVersion = phoneNumberCrypto.version,
                phoneNumberMasked = phoneNumberCrypto.mask(phoneNumber),
            )
        }

        return user
    }

    private fun createPhoneNumber(seed: String): String {
        // local 사용자 전화번호 suffix 생성
        val suffix = seed
            .hashCode()
            .let { abs(it.toLong()) }
            .rem(100_000_000)
            .toString()
            .padStart(8, '0')

        // local 사용자 전화번호 반환
        return "+8210$suffix"
    }

    companion object {
        private const val TOKEN_PREFIX = "local-test:"
    }
}
