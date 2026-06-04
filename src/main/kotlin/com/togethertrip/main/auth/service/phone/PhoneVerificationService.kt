package com.togethertrip.main.auth.service.phone

import com.togethertrip.main.auth.dto.request.ConfirmPhoneVerificationRequest
import com.togethertrip.main.auth.dto.request.RequestPhoneVerificationRequest
import com.togethertrip.main.auth.dto.response.PhoneVerificationCodeSentResponse
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.auth.service.oauth.OAuthTemporarySessionService
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.phone.PhoneNumberNormalizer
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

@Service
class PhoneVerificationService(
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
    private val temporarySessionService: OAuthTemporarySessionService,
    private val smsSender: SmsSender,
    private val userRepository: UserRepository,
    private val phoneVerificationStore: PhoneVerificationStore,
    private val phoneVerificationRateLimiter: PhoneVerificationRateLimiter,
) {

    fun requestCode(request: RequestPhoneVerificationRequest): PhoneVerificationCodeSentResponse {
        temporarySessionService.get(request.temporaryToken)

        val phoneNumber = phoneNumberNormalizer.normalize(request.phoneNumber)

        validatePhoneNumberAvailable(phoneNumber)
        smsSender.validateSendable()
        phoneVerificationRateLimiter.validate(phoneNumber)

        val code = createVerificationCode()
        val state = PhoneVerificationState(
            phoneNumber = phoneNumber,
            code = code,
            expiresAt = Instant.now().plus(CODE_TTL),
        )

        phoneVerificationStore.save(
            temporaryToken = request.temporaryToken,
            state = state,
            ttl = CODE_TTL,
        )

        smsSender.sendVerificationCode(
            phoneNumber = phoneNumber,
            code = code,
        )

        return PhoneVerificationCodeSentResponse(
            phoneNumber = phoneNumber,
            expiresInSeconds = CODE_TTL.seconds,
        )
    }

    fun confirmCode(request: ConfirmPhoneVerificationRequest): ConfirmedPhoneVerification {
        val session = temporarySessionService.get(request.temporaryToken)
        val phoneNumber = phoneNumberNormalizer.normalize(request.phoneNumber)
        val state = phoneVerificationStore.get(request.temporaryToken)

        validatePhoneNumberAvailable(phoneNumber)

        if (state.phoneNumber != phoneNumber) {
            throw BusinessException(AuthErrorCode.INVALID_PHONE_VERIFICATION_CODE)
        }

        if (Instant.now().isAfter(state.expiresAt)) {
            phoneVerificationStore.delete(request.temporaryToken)
            throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_CODE_EXPIRED)
        }

        if (state.attemptCount >= MAX_ATTEMPT_COUNT) {
            throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_ATTEMPT_EXCEEDED)
        }

        if (state.code != request.code) {
            phoneVerificationStore.saveAttemptFailure(
                temporaryToken = request.temporaryToken,
                state = state,
                maxAttemptCount = MAX_ATTEMPT_COUNT,
            )
            throw BusinessException(AuthErrorCode.INVALID_PHONE_VERIFICATION_CODE)
        }

        phoneVerificationStore.delete(request.temporaryToken)

        return ConfirmedPhoneVerification(
            session = session,
            phoneNumber = phoneNumber,
        )
    }

    fun deleteTemporarySession(temporaryToken: String) {
        temporarySessionService.delete(temporaryToken)
    }

    private fun validatePhoneNumberAvailable(phoneNumber: String) {
        val alreadyUsed = userRepository
            .existsByPhoneNumberAndDeletedAtIsNull(phoneNumber)

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
