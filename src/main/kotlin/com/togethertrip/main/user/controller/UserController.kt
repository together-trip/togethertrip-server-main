package com.togethertrip.main.user.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.user.controller.spec.UserApiSpec
import com.togethertrip.main.user.dto.request.SearchUserByNicknameRequest
import com.togethertrip.main.user.dto.request.SearchUserByPhoneRequest
import com.togethertrip.main.user.dto.request.UpdateUserMultipartRequest
import com.togethertrip.main.user.dto.request.UpdateUserRequest
import com.togethertrip.main.user.dto.response.MyTripParticipantResponse
import com.togethertrip.main.user.dto.response.NicknameAvailabilityResponse
import com.togethertrip.main.user.dto.response.PhoneUserSearchResponse
import com.togethertrip.main.user.dto.response.UserSearchResponse
import com.togethertrip.main.user.dto.response.UserResponse
import com.togethertrip.main.user.service.UserService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) : UserApiSpec {

    @PostMapping("/search/phone")
    override fun searchByPhoneNumber(
        @AuthenticationPrincipal authUser: AuthUser,
        @Valid @RequestBody request: SearchUserByPhoneRequest,
    ): ApiResponse<PhoneUserSearchResponse> {
        return ApiResponse.success(
            userService.searchByPhoneNumber(
                authUserId = authUser.userId,
                request = request,
            )
        )
    }

    @PostMapping("/search/nickname")
    override fun searchByNickname(
        @AuthenticationPrincipal authUser: AuthUser,
        @Valid @RequestBody request: SearchUserByNicknameRequest,
    ): ApiResponse<UserSearchResponse> {
        return ApiResponse.success(
            userService.searchByNickname(
                authUserId = authUser.userId,
                request = request,
            )
        )
    }

    @GetMapping("/me")
    override fun getMe(
        @AuthenticationPrincipal authUser: AuthUser,
    ): ApiResponse<UserResponse> {
        return ApiResponse.success(
            userService.getMe(authUser.userId)
        )
    }

    @GetMapping("/nicknames/availability")
    override fun checkNicknameAvailability(
        @RequestParam nickname: String,
    ): ApiResponse<NicknameAvailabilityResponse> {
        return ApiResponse.success(
            userService.checkNicknameAvailability(
                nickname = nickname,
            )
        )
    }

    @PatchMapping("/me", consumes = [MediaType.APPLICATION_JSON_VALUE])
    override fun updateMe(
        @AuthenticationPrincipal authUser: AuthUser,
        @Valid @RequestBody request: UpdateUserRequest,
    ): ApiResponse<UserResponse> {
        return ApiResponse.success(
            userService.updateMe(
                userId = authUser.userId,
                request = request,
            )
        )
    }

    @PatchMapping("/me", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    override fun updateMeWithMultipart(
        @AuthenticationPrincipal authUser: AuthUser,
        @Valid @ModelAttribute request: UpdateUserMultipartRequest,
    ): ApiResponse<UserResponse> {
        return ApiResponse.success(
            userService.updateMe(
                userId = authUser.userId,
                request = request.toUpdateUserRequest(),
                profileImage = request.profileImage,
            )
        )
    }

    @DeleteMapping("/me")
    override fun deleteMe(
        @AuthenticationPrincipal authUser: AuthUser,
    ): ApiResponse<Unit> {
        userService.deleteMe(authUser.userId)

        return ApiResponse.success()
    }

    @GetMapping("/me/trip-participants")
    override fun getMyTripParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestParam tripId: Long,
    ): ApiResponse<MyTripParticipantResponse> {
        return ApiResponse.success(
            userService.getMyTripParticipant(
                userId = authUser.userId,
                tripId = tripId,
            )
        )
    }
}
