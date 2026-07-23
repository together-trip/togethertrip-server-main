package com.togethertrip.main.auth.controller

import com.togethertrip.main.auth.dto.response.PhoneVerificationCodeSentResponse
import com.togethertrip.main.auth.service.oauth.OAuthTemporarySessionService
import com.togethertrip.main.auth.service.phone.PhoneVerificationState
import com.togethertrip.main.auth.service.phone.PhoneVerificationStore
import com.togethertrip.main.global.phone.PhoneNumberHasher
import com.togethertrip.main.global.phone.PhoneNumberNormalizer
import com.togethertrip.main.global.response.ApiResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

@RestController
@RequestMapping("/api/local-test/auth")
@ConditionalOnProperty(name = ["auth.local-test.enabled"], havingValue = "true")
class LocalTestAuthController(
    private val temporarySessionService: OAuthTemporarySessionService,
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
    private val phoneNumberHasher: PhoneNumberHasher,
    private val phoneVerificationStore: PhoneVerificationStore,
) {

    @PostMapping("/phone-verifications")
    fun seedPhoneVerification(
        @Valid @RequestBody request: LocalTestPhoneVerificationSeedRequest,
    ): ApiResponse<PhoneVerificationCodeSentResponse> {
        temporarySessionService.get(request.temporaryToken)

        val phoneNumber = phoneNumberNormalizer.normalize(request.phoneNumber)
        val state = PhoneVerificationState(
            phoneNumberHash = phoneNumberHasher.hash(phoneNumber),
            code = request.code,
            expiresAt = Instant.now().plus(CODE_TTL),
        )

        phoneVerificationStore.save(
            temporaryToken = request.temporaryToken,
            state = state,
            ttl = CODE_TTL,
        )

        return ApiResponse.success(
            PhoneVerificationCodeSentResponse(
                expiresInSeconds = CODE_TTL.seconds,
            )
        )
    }

    data class LocalTestPhoneVerificationSeedRequest(
        @field:NotBlank
        val temporaryToken: String,

        @field:NotBlank
        val phoneNumber: String,

        @field:Pattern(regexp = "\\d{6}")
        val code: String = DEFAULT_CODE,
    )

    companion object {
        private const val DEFAULT_CODE = "123456"
        private val CODE_TTL: Duration = Duration.ofMinutes(3)
    }
}
