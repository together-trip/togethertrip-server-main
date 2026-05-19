package com.togethertrip.main.auth.controller

import com.togethertrip.main.auth.dto.KakaoLoginRequest
import com.togethertrip.main.auth.dto.TokenRefreshRequest
import com.togethertrip.main.auth.dto.TokenResponse
import com.togethertrip.main.auth.service.AuthService
import com.togethertrip.main.global.response.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
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
}