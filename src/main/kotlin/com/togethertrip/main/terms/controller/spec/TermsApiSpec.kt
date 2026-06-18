package com.togethertrip.main.terms.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.terms.dto.request.SaveTermAgreementsRequest
import com.togethertrip.main.terms.dto.request.UpdateTermAgreementRequest
import com.togethertrip.main.terms.dto.response.TermAgreementStatusListResponse
import com.togethertrip.main.terms.dto.response.TermResponse
import com.togethertrip.main.user.domain.UserAgreementType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Terms", description = "약관 API")
interface TermsApiSpec {

    @Operation(summary = "약관 목록 조회", description = "현재 가입 및 설정 화면에서 사용하는 약관 목록과 최신 본문을 조회합니다.")
    fun getTerms(): ApiResponse<List<TermResponse>>

    @Operation(summary = "내 약관 동의 상태 조회", description = "현재 로그인한 사용자의 약관별 동의 상태를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun getMyAgreementStatus(
        authUser: AuthUser,
    ): ApiResponse<TermAgreementStatusListResponse>

    @Operation(summary = "내 약관 동의 저장", description = "가입 또는 프로필 완료 전후에 현재 사용자의 약관 동의 상태를 저장합니다. 필수 약관은 모두 최신 버전으로 동의해야 합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun saveMyAgreements(
        authUser: AuthUser,
        request: SaveTermAgreementsRequest,
    ): ApiResponse<TermAgreementStatusListResponse>

    @Operation(summary = "내 약관 동의 변경", description = "마이페이지에서 선택 약관 동의 상태를 변경합니다. 필수 약관은 철회할 수 없습니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun updateMyAgreement(
        authUser: AuthUser,
        code: UserAgreementType,
        request: UpdateTermAgreementRequest,
    ): ApiResponse<TermAgreementStatusListResponse>
}
