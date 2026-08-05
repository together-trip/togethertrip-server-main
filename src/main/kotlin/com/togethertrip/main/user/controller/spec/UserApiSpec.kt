package com.togethertrip.main.user.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.user.dto.request.SearchUserByNicknameRequest
import com.togethertrip.main.user.dto.request.UpdateUserMultipartRequest
import com.togethertrip.main.user.dto.request.UpdateUserRequest
import com.togethertrip.main.user.dto.response.MyTripParticipantResponse
import com.togethertrip.main.user.dto.response.NicknameAvailabilityResponse
import com.togethertrip.main.user.dto.response.UserSearchResponse
import com.togethertrip.main.user.dto.response.UserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User", description = "사용자 API")
@SecurityRequirement(name = "bearerAuth")
interface UserApiSpec {

    @Operation(summary = "닉네임으로 사용자 검색", description = "닉네임과 정확히 일치하는 활성 사용자를 검색합니다.")
    fun searchByNickname(
        authUser: AuthUser,
        request: SearchUserByNicknameRequest,
    ): ApiResponse<UserSearchResponse>

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자 정보를 조회합니다.")
    fun getMe(
        authUser: AuthUser,
    ): ApiResponse<UserResponse>

    @Operation(summary = "닉네임 중복 체크", description = "가입 또는 프로필 입력 전 닉네임 사용 가능 여부를 확인합니다.")
    fun checkNicknameAvailability(
        @Parameter(
            description = "중복 확인할 닉네임. 2~20자.",
            example = "여행자",
            required = true,
        )
        nickname: String,
    ): ApiResponse<NicknameAvailabilityResponse>

    @Operation(summary = "내 정보 수정", description = "application/json으로 현재 사용자의 닉네임, 성별, 생년월일, 프로필 이미지 URL을 수정합니다.")
    fun updateMe(
        authUser: AuthUser,
        request: UpdateUserRequest,
    ): ApiResponse<UserResponse>

    @Operation(summary = "내 정보 수정", description = "multipart/form-data로 현재 사용자의 닉네임, 성별, 생년월일, 프로필 이미지 파일을 수정합니다. profileImage 파일이 있으면 저장 후 프로필 이미지 URL로 반영합니다.")
    fun updateMeWithMultipart(
        authUser: AuthUser,
        request: UpdateUserMultipartRequest,
    ): ApiResponse<UserResponse>

    @Operation(
        summary = "회원 탈퇴",
        description = "현재 사용자의 개인정보와 인증 연결을 제거하고 정산·지출 원장은 익명화해 보존합니다.",
    )
    fun deleteMe(
        authUser: AuthUser,
    ): ApiResponse<Unit>

    @Operation(summary = "내 여행 참여자 정보 조회", description = "특정 여행에서 현재 사용자의 참여자 정보를 조회합니다.")
    fun getMyTripParticipant(
        authUser: AuthUser,
        tripId: Long,
    ): ApiResponse<MyTripParticipantResponse>
}
