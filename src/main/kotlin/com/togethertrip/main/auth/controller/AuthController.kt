package com.togethertrip.main.auth.controller

import com.togethertrip.main.auth.dto.KakaoLoginRequest
import com.togethertrip.main.auth.dto.TokenRefreshRequest
import com.togethertrip.main.auth.dto.TokenResponse
import com.togethertrip.main.auth.service.AuthService
import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {

    @Operation(summary = "카카오 로그인", description = "카카오 OAuth 액세스 토큰으로 로그인합니다.")
    @PostMapping("/oauth/kakao")
    fun loginWithKakao(
        @RequestBody request: KakaoLoginRequest,
    ): ApiResponse<TokenResponse> {
        return ApiResponse.success(
            authService.loginWithKakao(request)
        )
    }

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 새 액세스 토큰을 발급합니다.")
    @PostMapping("/refresh")
    fun refreshToken(
        @RequestBody request: TokenRefreshRequest,
    ): ApiResponse<TokenResponse> {
        return ApiResponse.success(
            authService.refreshToken(request)
        )
    }

    @Operation(summary = "로그아웃", description = "현재 사용자를 로그아웃합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal authUser: AuthUser,
    ): ApiResponse<Unit> {
        authService.logout(authUser.userId)

        return ApiResponse.success(
            data = Unit,
            message = "로그아웃되었습니다.",
        )
    }
}
