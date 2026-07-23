package com.togethertrip.main.terms.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.terms.controller.spec.TermsApiSpec
import com.togethertrip.main.terms.dto.request.SaveTermAgreementsRequest
import com.togethertrip.main.terms.dto.request.UpdateTermAgreementRequest
import com.togethertrip.main.terms.dto.response.TermAgreementStatusListResponse
import com.togethertrip.main.terms.dto.response.TermResponse
import com.togethertrip.main.terms.service.TermsService
import com.togethertrip.main.user.domain.UserAgreementType
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/terms")
class TermsController(
    private val termsService: TermsService,
) : TermsApiSpec {

    @GetMapping
    override fun getTerms(): ApiResponse<List<TermResponse>> {
        return ApiResponse.success(termsService.getTerms())
    }

    @GetMapping("/agreements/me")
    override fun getMyAgreementStatus(
        @AuthenticationPrincipal authUser: AuthUser,
    ): ApiResponse<TermAgreementStatusListResponse> {
        return ApiResponse.success(
            termsService.getMyAgreementStatus(authUser.userId)
        )
    }

    @PutMapping("/agreements")
    override fun saveMyAgreements(
        @AuthenticationPrincipal authUser: AuthUser,
        @Valid @RequestBody request: SaveTermAgreementsRequest,
    ): ApiResponse<TermAgreementStatusListResponse> {
        return ApiResponse.success(
            termsService.saveMyAgreements(
                userId = authUser.userId,
                request = request,
            )
        )
    }

    @PatchMapping("/agreements/{code}")
    override fun updateMyAgreement(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable code: UserAgreementType,
        @Valid @RequestBody request: UpdateTermAgreementRequest,
    ): ApiResponse<TermAgreementStatusListResponse> {
        return ApiResponse.success(
            termsService.updateMyAgreement(
                userId = authUser.userId,
                code = code,
                request = request,
            )
        )
    }
}
