package com.togethertrip.main.terms.dto.response

import com.togethertrip.main.terms.service.TermDefinition
import com.togethertrip.main.user.domain.UserAgreement
import com.togethertrip.main.user.domain.UserAgreementType
import java.time.Instant

data class TermAgreementStatusResponse(
    val code: UserAgreementType,
    val title: String,
    val required: Boolean,
    val currentVersion: String,
    val agreed: Boolean,
    val agreedVersion: String?,
    val agreedAt: Instant?,
    val revokedAt: Instant?,
) {
    companion object {
        fun of(
            term: TermDefinition,
            agreement: UserAgreement?,
        ): TermAgreementStatusResponse {
            return TermAgreementStatusResponse(
                code = term.code,
                title = term.title,
                required = term.required,
                currentVersion = term.version,
                agreed = agreement?.agreed == true && agreement.termVersion == term.version,
                agreedVersion = agreement?.termVersion,
                agreedAt = agreement?.agreedAt,
                revokedAt = agreement?.revokedAt,
            )
        }
    }
}
