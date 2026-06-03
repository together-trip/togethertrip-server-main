package com.togethertrip.main.user.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.ErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.dto.request.UpdateUserRequest
import com.togethertrip.main.user.dto.response.MyTripParticipantResponse
import com.togethertrip.main.user.dto.response.UserResponse
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val tripParticipantRepository: TripParticipantRepository,
) {

    fun getMe(userId: Long): UserResponse {
        return UserResponse.from(
            getActiveUser(userId)
        )
    }

    @Transactional
    fun updateMe(
        userId: Long,
        request: UpdateUserRequest,
    ): UserResponse {
        validateUpdateRequest(request)

        val user = getActiveUser(userId)

        user.updateProfile(
            nickname = request.nickname,
            profileImageUrl = request.profileImageUrl,
        )

        return UserResponse.from(user)
    }

    @Transactional
    fun deleteMe(userId: Long) {
        val user = getActiveUser(userId)

        user.withdraw()
    }

    fun getMyTripParticipant(
        userId: Long,
        tripId: Long,
    ): MyTripParticipantResponse {
        getActiveUser(userId)

        val tripParticipant = tripParticipantRepository
            .findByTripIdAndUserIdAndDeletedAtIsNull(
                tripId = tripId,
                userId = userId,
            )
            ?: throw BusinessException(ErrorCode.TRIP_PARTICIPANT_NOT_FOUND)

        return MyTripParticipantResponse.from(tripParticipant)
    }

    private fun getActiveUser(userId: Long): User {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(ErrorCode.INACTIVE_USER)
        }

        return user
    }

    private fun validateUpdateRequest(request: UpdateUserRequest) {
        if (request.nickname != null && request.nickname.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }
}
