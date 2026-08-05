package com.togethertrip.main.auth.controller

import com.togethertrip.main.auth.controller.spec.AuthApiSpec
import com.togethertrip.main.auth.dto.TokenResponse
import com.togethertrip.main.auth.dto.request.AppleLoginRequest
import com.togethertrip.main.auth.dto.request.KakaoLoginRequest
import com.togethertrip.main.auth.dto.request.TokenRefreshRequest
import com.togethertrip.main.auth.dto.response.AuthResponse
import com.togethertrip.main.auth.service.AuthService
import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) : AuthApiSpec {

    @PostMapping("/oauth/kakao")
    override fun loginWithKakao(
        @Valid @RequestBody request: KakaoLoginRequest,
    ): ApiResponse<AuthResponse> {
        return ApiResponse.success(
            authService.loginWithKakao(request)
        )
    }

    @PostMapping("/oauth/apple")
    override fun loginWithApple(
        @Valid @RequestBody request: AppleLoginRequest,
    ): ApiResponse<AuthResponse> {
        return ApiResponse.success(
            authService.loginWithApple(request)
        )
    }

    @PostMapping("/refresh")
    override fun refreshToken(
        @Valid @RequestBody request: TokenRefreshRequest,
    ): ApiResponse<TokenResponse> {
        return ApiResponse.success(
            authService.refreshToken(request)
        )
    }

    @PostMapping("/logout")
    override fun logout(
        @AuthenticationPrincipal authUser: AuthUser,
    ): ApiResponse<Unit> {
        authService.logout(authUser.userId)

        return ApiResponse.success(
            data = Unit,
            message = "로그아웃되었습니다.",
        )
    }
}
