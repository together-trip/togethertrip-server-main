package com.togethertrip.main.terms.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.terms.dto.request.SaveTermAgreementsRequest
import com.togethertrip.main.terms.dto.request.UpdateTermAgreementRequest
import com.togethertrip.main.terms.dto.response.TermAgreementStatusListResponse
import com.togethertrip.main.terms.dto.response.TermAgreementStatusResponse
import com.togethertrip.main.terms.dto.response.TermResponse
import com.togethertrip.main.terms.exception.TermsErrorCode
import com.togethertrip.main.user.domain.UserAgreement
import com.togethertrip.main.user.domain.UserAgreementType
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserAgreementRepository
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class TermsService(
    private val termCatalog: TermCatalog,
    private val userRepository: UserRepository,
    private val userAgreementRepository: UserAgreementRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getTerms(): List<TermResponse> {
        return termCatalog.getTerms().map(TermResponse::from)
    }

    @Transactional(readOnly = true)
    fun getMyAgreementStatus(userId: Long): TermAgreementStatusListResponse {
        getActiveUser(userId)
        return buildAgreementStatus(userId)
    }

    @Transactional
    fun saveMyAgreements(
        userId: Long,
        request: SaveTermAgreementsRequest,
    ): TermAgreementStatusListResponse {
        val user = getActiveUser(userId)
        val now = Instant.now(clock)
        val requestsByCode = request.agreements.associateBy { requireNotNull(it.code) }

        validateRequiredTermsAgreed(requestsByCode)

        request.agreements.forEach { agreementRequest ->
            val code = requireNotNull(agreementRequest.code)
            val agreed = requireNotNull(agreementRequest.agreed)
            val version = requireNotNull(agreementRequest.version)
            val term = termCatalog.getTerm(code)

            validateVersion(term, version)
            if (term.required && !agreed) {
                throw BusinessException(TermsErrorCode.REQUIRED_TERM_MISSING)
            }

            upsertAgreement(
                user = user,
                code = code,
                version = version,
                agreed = agreed,
                now = now,
            )
        }

        return buildAgreementStatus(userId)
    }

    @Transactional
    fun updateMyAgreement(
        userId: Long,
        code: UserAgreementType,
        request: UpdateTermAgreementRequest,
    ): TermAgreementStatusListResponse {
        val user = getActiveUser(userId)
        val term = termCatalog.getTerm(code)
        val version = requireNotNull(request.version)
        val agreed = requireNotNull(request.agreed)

        validateVersion(term, version)
        if (term.required && !agreed) {
            throw BusinessException(TermsErrorCode.REQUIRED_TERM_WITHDRAW_NOT_ALLOWED)
        }

        upsertAgreement(
            user = user,
            code = code,
            version = version,
            agreed = agreed,
            now = Instant.now(clock),
        )

        return buildAgreementStatus(userId)
    }

    private fun validateRequiredTermsAgreed(
        requestsByCode: Map<UserAgreementType, com.togethertrip.main.terms.dto.request.TermAgreementRequest>,
    ) {
        termCatalog.requiredTerms().forEach { term ->
            val agreement = requestsByCode[term.code]
                ?: throw BusinessException(TermsErrorCode.REQUIRED_TERM_MISSING)

            if (agreement.agreed != true) {
                throw BusinessException(TermsErrorCode.REQUIRED_TERM_MISSING)
            }

            validateVersion(term, requireNotNull(agreement.version))
        }
    }

    private fun validateVersion(
        term: TermDefinition,
        requestedVersion: String,
    ) {
        if (term.version != requestedVersion) {
            throw BusinessException(TermsErrorCode.TERM_VERSION_MISMATCH)
        }
    }

    private fun upsertAgreement(
        user: com.togethertrip.main.user.domain.User,
        code: UserAgreementType,
        version: String,
        agreed: Boolean,
        now: Instant,
    ) {
        val agreement = userAgreementRepository
            .findByUserIdAndAgreementTypeAndDeletedAtIsNull(
                userId = user.id,
                agreementType = code,
            )

        if (agreement == null) {
            userAgreementRepository.save(
                UserAgreement(
                    user = user,
                    agreementType = code,
                    agreed = agreed,
                    termVersion = version,
                    agreedAt = if (agreed) now else null,
                    revokedAt = if (agreed) null else now,
                ).apply {
                    createdAt = now
                    updatedAt = now
                }
            )
            return
        }

        if (agreed) {
            agreement.agree(termVersion = version, now = now)
        } else {
            agreement.revoke(now)
        }
    }

    private fun buildAgreementStatus(userId: Long): TermAgreementStatusListResponse {
        val agreementsByCode = userAgreementRepository
            .findAllByUserIdAndDeletedAtIsNull(userId)
            .associateBy { it.agreementType }

        return TermAgreementStatusListResponse(
            agreements = termCatalog.getTerms().map { term ->
                TermAgreementStatusResponse.of(
                    term = term,
                    agreement = agreementsByCode[term.code],
                )
            }
        )
    }

    private fun getActiveUser(userId: Long): com.togethertrip.main.user.domain.User {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        return user
    }
}
