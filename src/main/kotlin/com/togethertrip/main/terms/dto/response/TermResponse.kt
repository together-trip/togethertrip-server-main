package com.togethertrip.main.terms.dto.response

import com.togethertrip.main.terms.service.TermDefinition
import com.togethertrip.main.user.domain.UserAgreementType

data class TermResponse(
    val code: UserAgreementType,
    val title: String,
    val required: Boolean,
    val version: String,
    val content: String,
) {
    companion object {
        fun from(term: TermDefinition): TermResponse {
            return TermResponse(
                code = term.code,
                title = term.title,
                required = term.required,
                version = term.version,
                content = term.content,
            )
        }
    }
}
