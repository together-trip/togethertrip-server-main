package com.togethertrip.main.user.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.global.storage.ProfileImageUrlPolicy
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.UserRole
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.dto.request.UpdateUserRequest
import com.togethertrip.main.user.dto.response.UserResponse
import com.togethertrip.main.user.repository.UserRepository
import com.togethertrip.main.user.service.UserService
import com.togethertrip.main.user.service.storage.UserProfileImageStorage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import kotlin.test.assertEquals

class UserControllerTest {

    private val userService = CapturingUserService()
    private val mockMvc = MockMvcBuilders
        .standaloneSetup(UserController(userService))
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .build()

    @Test
    fun `multipart 내 정보 수정 요청을 DTO와 파일로 바인딩한다`() {
        val authUser = AuthUser(userId = 7L, role = UserRole.USER)
        val authentication = UsernamePasswordAuthenticationToken(
            authUser,
            null,
            authUser.getAuthorities(),
        )
        val profileImage = MockMultipartFile(
            "profileImage",
            "profile.jpg",
            "image/jpeg",
            "image-content".toByteArray(),
        )
        SecurityContextHolder.getContext().authentication = authentication
        try {
            mockMvc.perform(
                multipart(HttpMethod.PATCH, "/api/users/me")
                    .file(profileImage)
                    .param("nickname", "새닉네임")
                    .param("gender", "FEMALE")
                    .param("birthDate", "1995-05-01")
            )
                .andExpect(status().isOk)
        } finally {
            SecurityContextHolder.clearContext()
        }

        assertEquals(7L, userService.capturedUserId)
        assertEquals("새닉네임", userService.capturedRequest.nickname)
        assertEquals("FEMALE", userService.capturedRequest.gender)
        assertEquals(LocalDate.of(1995, 5, 1), userService.capturedRequest.birthDate)
        assertEquals("profile.jpg", userService.capturedProfileImage?.originalFilename)
        assertEquals("image/jpeg", userService.capturedProfileImage?.contentType)
    }

    private class CapturingUserService : UserService(
        userRepository = mock(UserRepository::class.java),
        tripParticipantRepository = mock(TripParticipantRepository::class.java),
        userProfileImageStorage = mock(UserProfileImageStorage::class.java),
        profileImageUrlPolicy = ProfileImageUrlPolicy(
            userProfileImagePublicUrlPrefix = "/uploads/user-profile-images",
        ),
    ) {
        var capturedUserId: Long? = null
        lateinit var capturedRequest: UpdateUserRequest
        var capturedProfileImage: MultipartFile? = null

        override fun updateMe(
            userId: Long,
            request: UpdateUserRequest,
            profileImage: MultipartFile?,
        ): UserResponse {
            capturedUserId = userId
            capturedRequest = request
            capturedProfileImage = profileImage

            return UserResponse(
                id = userId,
                nickname = request.nickname ?: "재완",
                gender = request.gender,
                birthDate = request.birthDate,
                profileImageUrl = "/uploads/user-profile-images/stored-profile.jpg",
                role = UserRole.USER,
                status = UserStatus.ACTIVE,
            )
        }
    }
}
