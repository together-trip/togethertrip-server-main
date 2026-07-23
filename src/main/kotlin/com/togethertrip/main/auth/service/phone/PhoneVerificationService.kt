package com.togethertrip.main.auth.service.phone

import com.togethertrip.main.auth.dto.request.ConfirmPhoneVerificationRequest
import com.togethertrip.main.auth.dto.request.RequestPhoneVerificationRequest
import com.togethertrip.main.auth.dto.response.PhoneVerificationCodeSentResponse
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.auth.service.oauth.OAuthTemporarySessionService
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.phone.PhoneNumberCrypto
import com.togethertrip.main.global.phone.PhoneNumberHasher
import com.togethertrip.main.global.phone.PhoneNumberNormalizer
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

@Service
class PhoneVerificationService(
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
    private val phoneNumberHasher: PhoneNumberHasher,
    private val phoneNumberCrypto: PhoneNumberCrypto,
    private val temporarySessionService: OAuthTemporarySessionService,
    private val smsSender: SmsSender,
    private val userRepository: UserRepository,
    private val phoneVerificationStore: PhoneVerificationStore,
    private val phoneVerificationRateLimiter: PhoneVerificationRateLimiter,
) {

    fun requestCode(request: RequestPhoneVerificationRequest): PhoneVerificationCodeSentResponse {
        temporarySessionService.get(request.temporaryToken)

        // 전화번호 정규화 및 hash 생성
        val phoneNumber = phoneNumberNormalizer.normalize(request.phoneNumber)
        val phoneNumberHash = phoneNumberHasher.hash(phoneNumber)

        // 인증번호 발송 전 검증
        validatePhoneNumberAvailable(phoneNumberHash)
        smsSender.validateSendable()
        phoneVerificationRateLimiter.validate(phoneNumberHash)

        val code = createVerificationCode()
        // Redis 인증 상태 생성
        val state = PhoneVerificationState(
            phoneNumberHash = phoneNumberHash,
            code = code,
            expiresAt = Instant.now().plus(CODE_TTL),
        )

        phoneVerificationStore.save(
            temporaryToken = request.temporaryToken,
            state = state,
            ttl = CODE_TTL,
        )

        // SMS 인증번호 발송
        smsSender.sendVerificationCode(
            phoneNumber = phoneNumber,
            code = code,
        )

        // 인증번호 요청 응답
        return PhoneVerificationCodeSentResponse(
            expiresInSeconds = CODE_TTL.seconds,
        )
    }

    fun confirmCode(request: ConfirmPhoneVerificationRequest): ConfirmedPhoneVerification {
        val session = temporarySessionService.get(request.temporaryToken)
        val phoneNumber = phoneNumberNormalizer.normalize(request.phoneNumber)
        val phoneNumberHash = phoneNumberHasher.hash(phoneNumber)
        val state = phoneVerificationStore.get(request.temporaryToken)

        // 인증번호 만료 확인
        if (Instant.now().isAfter(state.expiresAt)) {
            phoneVerificationStore.delete(request.temporaryToken)
            throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_CODE_EXPIRED)
        }

        // 인증번호 검증 실패 처리
        if (state.phoneNumberHash != phoneNumberHash || state.code != request.code) {
            phoneVerificationStore.incrementAttemptFailure(
                temporaryToken = request.temporaryToken,
                state = state,
                maxAttemptCount = MAX_ATTEMPT_COUNT,
            )
            throw BusinessException(AuthErrorCode.INVALID_PHONE_VERIFICATION_CODE)
        }

        // 인증 상태 삭제
        phoneVerificationStore.delete(request.temporaryToken)

        // 전화번호 인증 완료 결과
        return ConfirmedPhoneVerification(
            session = session,
            phoneNumberHash = phoneNumberHash,
            phoneNumberHashVersion = phoneNumberHasher.version,
            phoneNumberEncrypted = phoneNumberCrypto.encrypt(phoneNumber),
            phoneNumberEncryptionVersion = phoneNumberCrypto.version,
            phoneNumberMasked = phoneNumberCrypto.mask(phoneNumber),
        )
    }

    fun deleteTemporarySession(temporaryToken: String) {
        temporarySessionService.delete(temporaryToken)
    }

    private fun validatePhoneNumberAvailable(phoneNumberHash: String) {
        val alreadyUsed = userRepository
            .existsByPhoneNumberHashAndDeletedAtIsNull(phoneNumberHash)

        // 이미 사용 중인 전화번호 확인
        if (alreadyUsed) {
            throw BusinessException(AuthErrorCode.PHONE_NUMBER_ALREADY_USED)
        }
    }

    private fun createVerificationCode(): String {
        return SECURE_RANDOM
            .nextInt(1_000_000)
            .toString()
            .padStart(6, '0')
    }

    companion object {
        private val CODE_TTL: Duration = Duration.ofMinutes(3)
        private const val MAX_ATTEMPT_COUNT = 5
        private val SECURE_RANDOM = SecureRandom()
    }
}
