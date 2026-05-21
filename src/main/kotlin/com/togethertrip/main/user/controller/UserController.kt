package com.togethertrip.main.user.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.user.dto.request.UpdateUserRequest
import com.togethertrip.main.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User", description = "사용자 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) {

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자 정보를 조회합니다.")
    @GetMapping("/me")
    fun getMe(
        @AuthenticationPrincipal authUser: AuthUser,
    ) {
    }

    @Operation(summary = "내 정보 수정", description = "현재 사용자의 닉네임과 프로필 이미지를 수정합니다.")
    @PatchMapping("/me")
    fun updateMe(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestBody request: UpdateUserRequest,
    ) {
    }

    @Operation(summary = "회원 탈퇴", description = "현재 사용자를 탈퇴 처리합니다.")
    @DeleteMapping("/me")
    fun deleteMe(
        @AuthenticationPrincipal authUser: AuthUser,
    ) {
    }

    @Operation(summary = "내 여행 참여자 정보 조회", description = "특정 여행에서 현재 사용자의 참여자 정보를 조회합니다.")
    @GetMapping("/me/trip-participants")
    fun getMyTripParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestParam tripId: Long,
    ) {
    }
}
