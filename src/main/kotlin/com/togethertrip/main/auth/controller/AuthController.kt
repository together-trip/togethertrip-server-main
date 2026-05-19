package com.togethertrip.main.auth.controller

import com.togethertrip.main.auth.dto.KakaoLoginRequest
import com.togethertrip.main.auth.dto.TokenRefreshRequest
import com.togethertrip.main.auth.dto.TokenResponse
import com.togethertrip.main.auth.service.AuthService
import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/oauth/kakao")
    fun loginWithKakao(
        @RequestBody request: KakaoLoginRequest,
    ): ApiResponse<TokenResponse> {
        return ApiResponse.success(
            authService.loginWithKakao(request)
        )
    }

    @PostMapping("/refresh")
    fun refreshToken(
        @RequestBody request: TokenRefreshRequest,
    ): ApiResponse<TokenResponse> {
        return ApiResponse.success(
            authService.refreshToken(request)
        )
    }

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