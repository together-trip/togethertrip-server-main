package com.togethertrip.main.user.service

import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.repository.OAuthAccountRepository
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupTask
import com.togethertrip.main.user.repository.UserAccountDeletionCleanupTaskRepository
import com.togethertrip.main.user.service.storage.UserProfileImageStorage
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class UserAccountDeletionCleanupEnqueueService(
    private val oauthAccountRepository: OAuthAccountRepository,
    private val taskRepository: UserAccountDeletionCleanupTaskRepository,
    private val profileImageStorage: UserProfileImageStorage,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun enqueue(
        userId: Long,
        profileImageUrl: String?,
    ) {
        val now = Instant.now(clock)
        val tasks = oauthAccountRepository
            .findAllByUserIdAndProvider(userId, OAuthProvider.APPLE)
            .mapNotNull { account -> account.encryptedRefreshToken }
            .map { encryptedToken ->
                UserAccountDeletionCleanupTask.appleRefreshToken(userId, encryptedToken, now)
            }
            .toMutableList()

        if (profileImageUrl != null && profileImageStorage.isManagedFileUrl(profileImageUrl)) {
            tasks += UserAccountDeletionCleanupTask.profileImage(userId, profileImageUrl, now)
        }
        tasks += UserAccountDeletionCleanupTask.refreshToken(userId, now)

        taskRepository.saveAll(tasks)
    }
}
