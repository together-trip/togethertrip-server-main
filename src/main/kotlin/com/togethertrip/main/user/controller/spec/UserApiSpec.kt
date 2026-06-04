package com.togethertrip.main.user.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.user.dto.request.SearchUserByPhoneRequest
import com.togethertrip.main.user.dto.request.UpdateUserRequest
import com.togethertrip.main.user.dto.response.MyTripParticipantResponse
import com.togethertrip.main.user.dto.response.NicknameAvailabilityResponse
import com.togethertrip.main.user.dto.response.PhoneUserSearchResponse
import com.togethertrip.main.user.dto.response.UserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User", description = "사용자 API")
@SecurityRequirement(name = "bearerAuth")
interface UserApiSpec {

    @Operation(summary = "전화번호로 사용자 검색", description = "전화번호와 정확히 일치하는 인증 완료 활성 사용자를 검색합니다.")
    fun searchByPhoneNumber(
        authUser: AuthUser,
        request: SearchUserByPhoneRequest,
    ): ApiResponse<PhoneUserSearchResponse>

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자 정보를 조회합니다.")
    fun getMe(
        authUser: AuthUser,
    ): ApiResponse<UserResponse>

    @Operation(summary = "닉네임 중복 체크", description = "현재 사용자를 제외하고 닉네임 사용 가능 여부를 확인합니다.")
    fun checkNicknameAvailability(
        authUser: AuthUser,
        @Parameter(
            description = "중복 확인할 닉네임. 2~20자.",
            example = "여행자",
            required = true,
        )
        nickname: String,
    ): ApiResponse<NicknameAvailabilityResponse>

    @Operation(summary = "내 정보 수정", description = "현재 사용자의 닉네임, 성별, 생년월일, 프로필 이미지를 수정합니다.")
    fun updateMe(
        authUser: AuthUser,
        request: UpdateUserRequest,
    ): ApiResponse<UserResponse>

    @Operation(summary = "회원 탈퇴", description = "현재 사용자를 탈퇴 처리합니다.")
    fun deleteMe(
        authUser: AuthUser,
    ): ApiResponse<Unit>

    @Operation(summary = "내 여행 참여자 정보 조회", description = "특정 여행에서 현재 사용자의 참여자 정보를 조회합니다.")
    fun getMyTripParticipant(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<MyTripParticipantResponse>
}
