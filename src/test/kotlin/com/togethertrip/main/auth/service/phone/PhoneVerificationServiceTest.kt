package com.togethertrip.main.auth.service.phone

import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.dto.request.ConfirmPhoneVerificationRequest
import com.togethertrip.main.auth.dto.request.RequestPhoneVerificationRequest
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.auth.service.oauth.OAuthTemporarySession
import com.togethertrip.main.auth.service.oauth.OAuthTemporarySessionService
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.phone.PhoneNumberCrypto
import com.togethertrip.main.global.phone.PhoneNumberHasher
import com.togethertrip.main.global.phone.PhoneNumberNormalizer
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PhoneVerificationServiceTest {

    private lateinit var normalizer: PhoneNumberNormalizer
    private lateinit var hasher: PhoneNumberHasher
    private lateinit var crypto: PhoneNumberCrypto
    private lateinit var temporarySessionService: OAuthTemporarySessionService
    private lateinit var smsSender: SmsSender
    private lateinit var userRepository: UserRepository
    private lateinit var store: PhoneVerificationStore
    private lateinit var rateLimiter: PhoneVerificationRateLimiter
    private lateinit var service: PhoneVerificationService

    @BeforeEach
    fun setUp() {
        normalizer = mock(PhoneNumberNormalizer::class.java)
        hasher = mock(PhoneNumberHasher::class.java)
        crypto = mock(PhoneNumberCrypto::class.java)
        temporarySessionService = mock(OAuthTemporarySessionService::class.java)
        smsSender = mock(SmsSender::class.java)
        userRepository = mock(UserRepository::class.java)
        store = mock(PhoneVerificationStore::class.java)
        rateLimiter = mock(PhoneVerificationRateLimiter::class.java)
        service = PhoneVerificationService(
            phoneNumberNormalizer = normalizer,
            phoneNumberHasher = hasher,
            phoneNumberCrypto = crypto,
            temporarySessionService = temporarySessionService,
            smsSender = smsSender,
            userRepository = userRepository,
            phoneVerificationStore = store,
            phoneVerificationRateLimiter = rateLimiter,
        )
    }

    @Test
    fun `인증번호 요청은 6자리 code와 3분 상태를 저장한 뒤 SMS를 전송한다`() {
        stubPhoneLookup()
        `when`(temporarySessionService.get(TOKEN)).thenReturn(session())
        `when`(userRepository.existsByPhoneNumberHashAndDeletedAtIsNull(HASH)).thenReturn(false)

        val response = service.requestCode(RequestPhoneVerificationRequest(TOKEN, RAW_PHONE))

        verify(rateLimiter).validate(HASH)
        verify(smsSender).validateSendable()
        val saveInvocation = mockingDetails(store).invocations.single { it.method.name == "save" }
        val state = saveInvocation.arguments[1] as PhoneVerificationState
        assertEquals(TOKEN, saveInvocation.arguments[0])
        assertEquals(Duration.ofMinutes(3), saveInvocation.arguments[2])
        assertEquals(HASH, state.phoneNumberHash)
        assertTrue(state.code.matches(Regex("\\d{6}")))
        assertTrue(state.expiresAt.isAfter(Instant.now().plusSeconds(175)))
        verify(smsSender).sendVerificationCode(NORMALIZED_PHONE, state.code)
        assertEquals(180L, response.expiresInSeconds)
    }

    @Test
    fun `이미 사용 중인 전화번호는 rate limit과 SMS 전에 거부한다`() {
        stubPhoneLookup()
        `when`(temporarySessionService.get(TOKEN)).thenReturn(session())
        `when`(userRepository.existsByPhoneNumberHashAndDeletedAtIsNull(HASH)).thenReturn(true)

        val exception = assertFailsWith<BusinessException> {
            service.requestCode(RequestPhoneVerificationRequest(TOKEN, RAW_PHONE))
        }

        assertEquals(AuthErrorCode.PHONE_NUMBER_ALREADY_USED, exception.errorCode)
        verify(rateLimiter, never()).validate(HASH)
        verify(smsSender, never()).validateSendable()
    }

    @Test
    fun `정상 code 확인은 상태를 삭제하고 hash 암호문 버전과 masking을 반환한다`() {
        val session = session()
        stubPhoneLookup()
        `when`(temporarySessionService.get(TOKEN)).thenReturn(session)
        `when`(store.get(TOKEN)).thenReturn(validState())
        `when`(hasher.version).thenReturn("hash-v1")
        `when`(crypto.encrypt(NORMALIZED_PHONE)).thenReturn("encrypted-phone")
        `when`(crypto.version).thenReturn("enc-v1")
        `when`(crypto.mask(NORMALIZED_PHONE)).thenReturn("010-****-5678")

        val result = service.confirmCode(confirmRequest(CODE))

        verify(store).delete(TOKEN)
        assertEquals(session, result.session)
        assertEquals(HASH, result.phoneNumberHash)
        assertEquals("hash-v1", result.phoneNumberHashVersion)
        assertEquals("encrypted-phone", result.phoneNumberEncrypted)
        assertEquals("enc-v1", result.phoneNumberEncryptionVersion)
        assertEquals("010-****-5678", result.phoneNumberMasked)
    }

    @Test
    fun `만료 시각이 지난 상태는 삭제하고 code expired를 반환한다`() {
        stubPhoneLookup()
        `when`(temporarySessionService.get(TOKEN)).thenReturn(session())
        `when`(store.get(TOKEN)).thenReturn(validState().copy(expiresAt = Instant.now().minusSeconds(1)))

        val exception = assertFailsWith<BusinessException> { service.confirmCode(confirmRequest(CODE)) }

        assertEquals(AuthErrorCode.PHONE_VERIFICATION_CODE_EXPIRED, exception.errorCode)
        verify(store).delete(TOKEN)
    }

    @Test
    fun `code 불일치는 실패 횟수를 증가시키고 invalid code를 반환한다`() {
        val state = validState()
        stubPhoneLookup()
        `when`(temporarySessionService.get(TOKEN)).thenReturn(session())
        `when`(store.get(TOKEN)).thenReturn(state)

        val exception = assertFailsWith<BusinessException> { service.confirmCode(confirmRequest("000000")) }

        assertEquals(AuthErrorCode.INVALID_PHONE_VERIFICATION_CODE, exception.errorCode)
        verify(store).incrementAttemptFailure(TOKEN, state, 5)
    }

    @Test
    fun `다섯 번째 code 불일치는 store의 attempt exceeded 오류를 그대로 반환한다`() {
        val state = validState()
        stubPhoneLookup()
        `when`(temporarySessionService.get(TOKEN)).thenReturn(session())
        `when`(store.get(TOKEN)).thenReturn(state)
        `when`(store.incrementAttemptFailure(TOKEN, state, 5)).thenThrow(
            BusinessException(AuthErrorCode.PHONE_VERIFICATION_ATTEMPT_EXCEEDED)
        )

        val exception = assertFailsWith<BusinessException> { service.confirmCode(confirmRequest("000000")) }

        assertEquals(AuthErrorCode.PHONE_VERIFICATION_ATTEMPT_EXCEEDED, exception.errorCode)
    }

    private fun stubPhoneLookup() {
        `when`(normalizer.normalize(RAW_PHONE)).thenReturn(NORMALIZED_PHONE)
        `when`(hasher.hash(NORMALIZED_PHONE)).thenReturn(HASH)
    }

    private fun validState(): PhoneVerificationState {
        return PhoneVerificationState(HASH, CODE, Instant.now().plusSeconds(180))
    }

    private fun confirmRequest(code: String): ConfirmPhoneVerificationRequest {
        return ConfirmPhoneVerificationRequest(TOKEN, RAW_PHONE, code)
    }

    private fun session(): OAuthTemporarySession {
        return OAuthTemporarySession(
            provider = OAuthProvider.KAKAO,
            providerUserId = "provider-user",
            nickname = "사용자",
            profileImageUrl = null,
            existingUserId = null,
        )
    }

    private companion object {
        const val TOKEN = "temporary-token"
        const val RAW_PHONE = "01012345678"
        const val NORMALIZED_PHONE = "+821012345678"
        const val HASH = "phone-hash"
        const val CODE = "123456"
    }
}
