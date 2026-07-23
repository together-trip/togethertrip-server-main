package com.togethertrip.main.terms.dto.request

import com.togethertrip.main.user.domain.UserAgreementType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class SaveTermAgreementsRequest(
    @field:Valid
    @field:NotEmpty
    val agreements: List<TermAgreementRequest>,
)

data class TermAgreementRequest(
    @field:NotNull
    val code: UserAgreementType?,

    @field:NotNull
    val version: String?,

    @field:NotNull
    val agreed: Boolean?,
)
