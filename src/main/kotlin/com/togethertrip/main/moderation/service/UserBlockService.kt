package com.togethertrip.main.moderation.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.moderation.dto.response.BlockedUserResponse
import com.togethertrip.main.moderation.dto.response.BlockedUsersResponse
import com.togethertrip.main.moderation.exception.ModerationErrorCode
import com.togethertrip.main.moderation.repository.UserBlockRepository
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserBlockService(
    private val userRepository: UserRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val userBlockRepository: UserBlockRepository,
) {
    @Transactional
    fun block(userId: Long, blockedUserId: Long): BlockedUserResponse {
        if (userId == blockedUserId) throw BusinessException(ModerationErrorCode.SELF_BLOCK_NOT_ALLOWED)
        getUser(userId)
        getUser(blockedUserId)
        if (!tripParticipantRepository.existsSharedActiveTrip(userId, blockedUserId)) {
            throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
        }
        userBlockRepository.findByBlockerIdAndBlockedIdAndDeletedAtIsNull(userId, blockedUserId)?.let {
            return BlockedUserResponse.from(it)
        }
        userBlockRepository.insertIfAbsent(userId, blockedUserId)
        val saved = userBlockRepository.findByBlockerIdAndBlockedIdAndDeletedAtIsNull(userId, blockedUserId)
            ?: throw BusinessException(ModerationErrorCode.BLOCK_NOT_FOUND)
        return BlockedUserResponse.from(saved)
    }

    @Transactional
    fun unblock(userId: Long, blockedUserId: Long) {
        val block = userBlockRepository.findByBlockerIdAndBlockedIdAndDeletedAtIsNull(userId, blockedUserId)
            ?: throw BusinessException(ModerationErrorCode.BLOCK_NOT_FOUND)
        block.markDeleted()
    }

    fun getBlocks(userId: Long): BlockedUsersResponse {
        getUser(userId)
        return BlockedUsersResponse(
            userBlockRepository.findAllByBlockerIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .map(BlockedUserResponse::from)
        )
    }

    private fun getUser(id: Long) = userRepository.findByIdAndDeletedAtIsNull(id)
        ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
}
