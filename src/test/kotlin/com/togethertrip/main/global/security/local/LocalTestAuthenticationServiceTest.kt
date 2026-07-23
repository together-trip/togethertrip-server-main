package com.togethertrip.main.global.security.local

import com.togethertrip.main.global.phone.PhoneNumberCrypto
import com.togethertrip.main.global.phone.PhoneNumberHasher
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserRole
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalTestAuthenticationServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var phoneNumberHasher: PhoneNumberHasher
    private lateinit var phoneNumberCrypto: PhoneNumberCrypto

    @BeforeEach
    fun setUp() {
        userRepository = mock(UserRepository::class.java) { invocation ->
            if (invocation.method.name == "save") {
                (invocation.arguments[0] as User).apply { id = 100L }
            } else {
                Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        }
        phoneNumberHasher = mock(PhoneNumberHasher::class.java) { invocation ->
            when (invocation.method.name) {
                "getVersion" -> "h1"
                "hash" -> "hashed-phone"
                else -> Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        }
        phoneNumberCrypto = mock(PhoneNumberCrypto::class.java) { invocation ->
            when (invocation.method.name) {
                "getVersion" -> "e1"
                "encrypt" -> "encrypted-phone"
                "mask" -> "+82 10-****-1234"
                else -> Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        }
    }

    @Test
    fun `비활성화되거나 prefix가 다른 token은 인증하지 않는다`() {
        val disabled = service(enabled = false)
        assertNull(disabled.authenticate("local-test:verified:user"))

        val enabled = service(enabled = true)
        assertNull(enabled.authenticate("Bearer local-test:verified:user"))
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `알 수 없는 local token mode는 인증하지 않는다`() {
        assertNull(service().authenticate("local-test:unknown:user"))
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `verified token은 사용자를 만들고 결정적 전화번호 인증 정보를 채운다`() {
        `when`(userRepository.findByNicknameAndDeletedAtIsNull("로컬 verified jaewan"))
            .thenReturn(null)

        val authUser = service().authenticate("local-test:verified:jaewan")

        assertEquals(100L, authUser?.userId)
        assertEquals(UserRole.USER, authUser?.role)
        val saved = savedUser()
        assertEquals("로컬 verified jaewan", saved.nickname)
        assertEquals("hashed-phone", saved.phoneNumberHash)
        assertEquals("h1", saved.phoneNumberHashVersion)
        assertEquals("encrypted-phone", saved.phoneNumberEncrypted)
        assertEquals("e1", saved.phoneNumberEncryptionVersion)
        assertEquals("+82 10-****-1234", saved.phoneNumberMasked)
        assertNotNull(saved.phoneVerifiedAt)
        verify(phoneNumberHasher).hash(validLocalPhone())
        verify(phoneNumberCrypto).encrypt(validLocalPhone())
        verify(phoneNumberCrypto).mask(validLocalPhone())
    }

    @Test
    fun `빈 verified 식별자는 swagger 사용자로 정규화한다`() {
        `when`(userRepository.findByNicknameAndDeletedAtIsNull("로컬 verified swagger"))
            .thenReturn(null)

        val authUser = service().authenticate("local-test:verified:   ")

        assertEquals(100L, authUser?.userId)
        assertEquals("로컬 verified swagger", savedUser().nickname)
    }

    @Test
    fun `이미 전화번호 인증된 verified 사용자는 암호화 값을 다시 만들지 않는다`() {
        val existing = user("로컬 verified existing", UserRole.USER).apply {
            verifyPhoneNumberHash(
                phoneNumberHash = "existing-hash",
                phoneNumberHashVersion = "h0",
                phoneNumberEncrypted = "existing-encrypted",
                phoneNumberEncryptionVersion = "e0",
                phoneNumberMasked = "existing-mask",
            )
        }
        `when`(userRepository.findByNicknameAndDeletedAtIsNull(existing.nickname)).thenReturn(existing)

        val authUser = service().authenticate("local-test:verified:existing")

        assertEquals(existing.id, authUser?.userId)
        assertEquals("existing-hash", existing.phoneNumberHash)
        assertEquals(0, invocationCount(userRepository, "save"))
        assertNoPhoneGeneration()
    }

    @Test
    fun `unverified token은 전화번호 정보 없이 일반 사용자를 만든다`() {
        `when`(userRepository.findByNicknameAndDeletedAtIsNull("로컬 unverified guest"))
            .thenReturn(null)

        val authUser = service().authenticate("local-test:unverified:guest")

        assertEquals(UserRole.USER, authUser?.role)
        val saved = savedUser()
        assertEquals("로컬 unverified guest", saved.nickname)
        assertNull(saved.phoneVerifiedAt)
        assertNoPhoneGeneration()
    }

    @Test
    fun `admin token은 새 관리자와 전화번호 인증 정보를 만든다`() {
        `when`(userRepository.findByNicknameAndDeletedAtIsNull("로컬 admin")).thenReturn(null)

        val authUser = service().authenticate("local-test:admin")

        assertEquals(100L, authUser?.userId)
        assertEquals(UserRole.ADMIN, authUser?.role)
        val saved = savedUser()
        assertEquals(UserRole.ADMIN, saved.role)
        assertNotNull(saved.phoneVerifiedAt)
    }

    @Test
    fun `기존 admin 사용자의 권한을 보정하되 인증된 전화번호는 유지한다`() {
        val existing = user("로컬 admin", UserRole.USER).apply {
            verifyPhoneNumberHash(
                phoneNumberHash = "existing-hash",
                phoneNumberHashVersion = "h0",
                phoneNumberEncrypted = "existing-encrypted",
                phoneNumberEncryptionVersion = "e0",
                phoneNumberMasked = "existing-mask",
            )
        }
        `when`(userRepository.findByNicknameAndDeletedAtIsNull("로컬 admin")).thenReturn(existing)

        val authUser = service().authenticate("local-test:admin:any-extra-part")

        assertEquals(existing.id, authUser?.userId)
        assertEquals(UserRole.ADMIN, authUser?.role)
        assertEquals(UserRole.ADMIN, existing.role)
        assertEquals("existing-hash", existing.phoneNumberHash)
        assertEquals(0, invocationCount(userRepository, "save"))
        assertNoPhoneGeneration()
    }

    private fun service(enabled: Boolean = true): LocalTestAuthenticationService {
        return LocalTestAuthenticationService(
            userRepository = userRepository,
            phoneNumberHasher = phoneNumberHasher,
            phoneNumberCrypto = phoneNumberCrypto,
            enabled = enabled,
        )
    }

    private fun user(nickname: String, role: UserRole): User {
        return User(
            nickname = nickname,
            role = role,
            status = UserStatus.ACTIVE,
        ).apply { id = 77L }
    }

    private fun savedUser(): User {
        val invocation = org.mockito.Mockito.mockingDetails(userRepository).invocations
            .last { it.method.name == "save" }
        return invocation.arguments[0] as User
    }

    private fun validLocalPhone(): String {
        val invocation = org.mockito.Mockito.mockingDetails(phoneNumberHasher).invocations
            .last { it.method.name == "hash" }
        return invocation.arguments[0] as String
    }

    private fun assertNoPhoneGeneration() {
        assertTrue(
            org.mockito.Mockito.mockingDetails(phoneNumberHasher).invocations
                .none { it.method.name == "hash" }
        )
        assertTrue(
            org.mockito.Mockito.mockingDetails(phoneNumberCrypto).invocations
                .none { it.method.name == "encrypt" || it.method.name == "mask" }
        )
    }

    private fun invocationCount(mock: Any, methodName: String): Int {
        return org.mockito.Mockito.mockingDetails(mock).invocations.count { it.method.name == methodName }
    }
}
