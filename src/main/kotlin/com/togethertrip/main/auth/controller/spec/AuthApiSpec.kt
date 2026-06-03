package com.togethertrip.main.auth.controller.spec

import com.togethertrip.main.auth.dto.KakaoLoginRequest
import com.togethertrip.main.auth.dto.TokenRefreshRequest
import com.togethertrip.main.auth.dto.TokenResponse
import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Auth", description = "인증 API")
interface AuthApiSpec {

    @Operation(summary = "카카오 로그인", description = "카카오 OAuth 액세스 토큰으로 로그인합니다.")
    fun loginWithKakao(
        request: KakaoLoginRequest,
    ): ApiResponse<TokenResponse>

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 새 액세스 토큰을 발급합니다.")
    fun refreshToken(
        request: TokenRefreshRequest,
    ): ApiResponse<TokenResponse>

    @Operation(summary = "로그아웃", description = "현재 사용자를 로그아웃합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun logout(
        authUser: AuthUser,
    ): ApiResponse<Unit>
}
