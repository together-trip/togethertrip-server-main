package com.togethertrip.main.auth.controller.spec

import com.togethertrip.main.auth.dto.response.AuthResponse
import com.togethertrip.main.auth.dto.request.KakaoLoginRequest
import com.togethertrip.main.auth.dto.request.TokenRefreshRequest
import com.togethertrip.main.auth.dto.TokenResponse
import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Auth", description = "인증 API")
interface AuthApiSpec {

    @Operation(
        summary = "카카오 로그인",
        description = "카카오 OAuth 액세스 토큰으로 로그인합니다. local 프로필에서는 accessToken에 local-test:{id}를 넣어 카카오 사용자 조회만 우회할 수 있습니다. 예: local-test:swagger",
    )
    @SecurityRequirements
    fun loginWithKakao(
        request: KakaoLoginRequest,
    ): ApiResponse<AuthResponse>

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 새 액세스 토큰을 발급합니다.")
    @SecurityRequirements
    fun refreshToken(
        request: TokenRefreshRequest,
    ): ApiResponse<TokenResponse>

    @Operation(summary = "로그아웃", description = "현재 사용자를 로그아웃합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun logout(
        authUser: AuthUser,
    ): ApiResponse<Unit>
}
