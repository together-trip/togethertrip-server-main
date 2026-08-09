package com.togethertrip.main.user.service

import com.togethertrip.main.auth.service.RefreshTokenService
import com.togethertrip.main.auth.service.apple.OAuthAccountRevoker
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupErrorCode
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupType
import com.togethertrip.main.user.repository.UserRepository
import com.togethertrip.main.user.service.storage.UserProfileImageStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UserAccountDeletionCleanupDispatchService(
    private val taskLifecycleService: UserAccountDeletionCleanupTaskLifecycleService,
    private val oauthAccountRevoker: OAuthAccountRevoker,
    private val profileImageStorage: UserProfileImageStorage,
    private val refreshTokenService: RefreshTokenService,
    private val userRepository: UserRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun dispatchDue(limit: Int = DEFAULT_LIMIT): UserAccountDeletionCleanupDispatchResult {
        val claims = taskLifecycleService.claimDue(limit.coerceIn(1, MAX_LIMIT))
        var completedCount = 0
        var failedCount = 0

        claims.forEach { claim ->
            if (dispatch(claim)) {
                completedCount += 1
            } else {
                failedCount += 1
            }
        }

        return UserAccountDeletionCleanupDispatchResult(
            requestedCount = claims.size,
            completedCount = completedCount,
            failedCount = failedCount,
        )
    }

    private fun dispatch(
        claim: UserAccountDeletionCleanupClaim,
    ): Boolean {
        try {
            execute(claim)
        } catch (exception: Exception) {
            val errorCode = errorCodeFor(claim.type, exception)
            persistFailure(claim, errorCode)
            logger.warn(
                "Account deletion cleanup failed. taskId={}, userId={}, taskType={}, errorCode={}, exceptionType={}",
                claim.taskId,
                claim.userId,
                claim.type,
                errorCode,
                exception.javaClass.simpleName,
            )
            return false
        }

        return try {
            val completed = taskLifecycleService.complete(claim)
            if (!completed) {
                logger.warn(
                    "Account deletion cleanup claim expired before completion. taskId={}, userId={}, taskType={}",
                    claim.taskId,
                    claim.userId,
                    claim.type,
                )
            }
            completed
        } catch (stateException: Exception) {
            logger.error(
                "Account deletion cleanup completion state persistence failed. " +
                    "taskId={}, userId={}, taskType={}, exceptionType={}",
                claim.taskId,
                claim.userId,
                claim.type,
                stateException.javaClass.simpleName,
            )
            false
        }
    }

    private fun persistFailure(
        claim: UserAccountDeletionCleanupClaim,
        errorCode: UserAccountDeletionCleanupErrorCode,
    ) {
        runCatching { taskLifecycleService.fail(claim, errorCode) }
            .onFailure { stateException ->
                logger.error(
                    "Account deletion cleanup failure state persistence failed. " +
                        "taskId={}, userId={}, taskType={}, exceptionType={}",
                    claim.taskId,
                    claim.userId,
                    claim.type,
                    stateException.javaClass.simpleName,
                )
            }
    }

    private fun execute(claim: UserAccountDeletionCleanupClaim) {
        when (claim.type) {
            UserAccountDeletionCleanupType.APPLE_REFRESH_TOKEN ->
                oauthAccountRevoker.revokeEncrypted(requirePayload(claim))

            UserAccountDeletionCleanupType.PROFILE_IMAGE -> {
                val profileImageUrl = requirePayload(claim)
                if (!isCurrentlyReferenced(claim.userId, profileImageUrl)) {
                    profileImageStorage.deleteByFileUrl(profileImageUrl)
                }
            }

            UserAccountDeletionCleanupType.REDIS_REFRESH_TOKEN ->
                refreshTokenService.delete(claim.userId)
        }
    }

    private fun requirePayload(claim: UserAccountDeletionCleanupClaim): String {
        return claim.payload ?: throw InvalidCleanupTaskPayloadException()
    }

    private fun isCurrentlyReferenced(
        userId: Long,
        profileImageUrl: String,
    ): Boolean {
        return userRepository.existsByIdAndProfileImageUrlAndDeletedAtIsNull(
            id = userId,
            profileImageUrl = profileImageUrl,
        )
    }

    private fun errorCodeFor(
        type: UserAccountDeletionCleanupType,
        exception: Exception,
    ): UserAccountDeletionCleanupErrorCode {
        if (exception is InvalidCleanupTaskPayloadException) {
            return UserAccountDeletionCleanupErrorCode.INVALID_TASK_PAYLOAD
        }

        return when (type) {
            UserAccountDeletionCleanupType.APPLE_REFRESH_TOKEN ->
                UserAccountDeletionCleanupErrorCode.APPLE_TOKEN_REVOCATION_FAILED

            UserAccountDeletionCleanupType.PROFILE_IMAGE ->
                UserAccountDeletionCleanupErrorCode.PROFILE_IMAGE_DELETE_FAILED

            UserAccountDeletionCleanupType.REDIS_REFRESH_TOKEN ->
                UserAccountDeletionCleanupErrorCode.REDIS_REFRESH_TOKEN_DELETE_FAILED
        }
    }

    private class InvalidCleanupTaskPayloadException : IllegalStateException()

    private companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 500
    }
}
