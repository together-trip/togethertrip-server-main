package com.togethertrip.main.terms.dto.request

import jakarta.validation.constraints.NotNull

data class UpdateTermAgreementRequest(
    @field:NotNull
    val version: String?,

    @field:NotNull
    val agreed: Boolean?,
)
