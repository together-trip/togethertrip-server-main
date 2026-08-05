package com.togethertrip.main.user.service

import com.togethertrip.main.auth.service.RefreshTokenService
import com.togethertrip.main.auth.service.apple.OAuthAccountRevoker
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupErrorCode
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupTask
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupType
import com.togethertrip.main.user.repository.UserAccountDeletionCleanupTaskRepository
import com.togethertrip.main.user.service.storage.UserProfileImageStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class UserAccountDeletionCleanupDispatchService(
    private val taskRepository: UserAccountDeletionCleanupTaskRepository,
    private val oauthAccountRevoker: OAuthAccountRevoker,
    private val profileImageStorage: UserProfileImageStorage,
    private val refreshTokenService: RefreshTokenService,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun dispatchDue(limit: Int = DEFAULT_LIMIT): UserAccountDeletionCleanupDispatchResult {
        val now = Instant.now(clock)
        val tasks = taskRepository.findDueForUpdate(now, limit.coerceIn(1, MAX_LIMIT))
        var completedCount = 0
        var failedCount = 0

        tasks.forEach { task ->
            if (dispatch(task, now)) {
                completedCount += 1
            } else {
                failedCount += 1
            }
        }

        return UserAccountDeletionCleanupDispatchResult(
            requestedCount = tasks.size,
            completedCount = completedCount,
            failedCount = failedCount,
        )
    }

    private fun dispatch(
        task: UserAccountDeletionCleanupTask,
        now: Instant,
    ): Boolean {
        return try {
            execute(task)
            task.markCompleted(now)
            true
        } catch (exception: Exception) {
            val errorCode = errorCodeFor(task.type, exception)
            task.markFailed(errorCode, now)
            logger.warn(
                "Account deletion cleanup failed. taskId={}, userId={}, taskType={}, errorCode={}, exceptionType={}",
                task.id,
                task.userId,
                task.type,
                errorCode,
                exception.javaClass.simpleName,
            )
            false
        }
    }

    private fun execute(task: UserAccountDeletionCleanupTask) {
        when (task.type) {
            UserAccountDeletionCleanupType.APPLE_REFRESH_TOKEN ->
                oauthAccountRevoker.revokeEncrypted(requirePayload(task))

            UserAccountDeletionCleanupType.PROFILE_IMAGE ->
                profileImageStorage.deleteByFileUrl(requirePayload(task))

            UserAccountDeletionCleanupType.REDIS_REFRESH_TOKEN ->
                refreshTokenService.delete(task.userId)
        }
    }

    private fun requirePayload(task: UserAccountDeletionCleanupTask): String {
        return task.payload ?: throw InvalidCleanupTaskPayloadException()
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
