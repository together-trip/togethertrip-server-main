package com.togethertrip.main.user.domain

import com.togethertrip.main.global.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import kotlin.math.min

@Entity
@Table(name = "user_account_deletion_cleanup_tasks")
class UserAccountDeletionCleanupTask private constructor(

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 40)
    var type: UserAccountDeletionCleanupType,

    @Column(name = "payload", length = 2000)
    var payload: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserAccountDeletionCleanupStatus = UserAccountDeletionCleanupStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Instant,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "claim_id", length = 36)
    var claimId: String? = null,

    @Column(name = "lease_expires_at")
    var leaseExpiresAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "last_error_code", length = 60)
    var lastErrorCode: UserAccountDeletionCleanupErrorCode? = null,
) : BaseEntity(createdAt = nextAttemptAt, updatedAt = nextAttemptAt) {

    fun markProcessing(
        newClaimId: String,
        newLeaseExpiresAt: Instant,
        now: Instant,
    ) {
        if (status == UserAccountDeletionCleanupStatus.PROCESSING) {
            retryCount += 1
            lastErrorCode = UserAccountDeletionCleanupErrorCode.WORKER_LEASE_EXPIRED
        }
        status = UserAccountDeletionCleanupStatus.PROCESSING
        claimId = newClaimId
        leaseExpiresAt = newLeaseExpiresAt
        updatedAt = now
    }

    fun markCompleted(now: Instant) {
        status = UserAccountDeletionCleanupStatus.COMPLETED
        payload = null
        completedAt = now
        claimId = null
        leaseExpiresAt = null
        lastErrorCode = null
        updatedAt = now
    }

    fun markFailed(
        errorCode: UserAccountDeletionCleanupErrorCode,
        now: Instant,
    ) {
        status = UserAccountDeletionCleanupStatus.FAILED
        retryCount += 1
        nextAttemptAt = now.plusSeconds(calculateBackoffSeconds(retryCount))
        lastErrorCode = errorCode
        claimId = null
        leaseExpiresAt = null
        updatedAt = now
    }

    companion object {
        private const val INITIAL_BACKOFF_SECONDS = 30L
        private const val MAX_BACKOFF_SECONDS = 21_600L
        private const val MAX_BACKOFF_EXPONENT = 20

        fun appleRefreshToken(
            userId: Long,
            encryptedRefreshToken: String,
            now: Instant,
        ) = UserAccountDeletionCleanupTask(
            userId = userId,
            type = UserAccountDeletionCleanupType.APPLE_REFRESH_TOKEN,
            payload = encryptedRefreshToken,
            nextAttemptAt = now,
        )

        fun profileImage(
            userId: Long,
            profileImageUrl: String,
            now: Instant,
        ) = UserAccountDeletionCleanupTask(
            userId = userId,
            type = UserAccountDeletionCleanupType.PROFILE_IMAGE,
            payload = profileImageUrl,
            nextAttemptAt = now,
        )

        fun refreshToken(
            userId: Long,
            now: Instant,
        ) = UserAccountDeletionCleanupTask(
            userId = userId,
            type = UserAccountDeletionCleanupType.REDIS_REFRESH_TOKEN,
            nextAttemptAt = now,
        )

        private fun calculateBackoffSeconds(retryCount: Int): Long {
            val exponent = (retryCount - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
            val delay = INITIAL_BACKOFF_SECONDS * (1L shl exponent)
            return min(delay, MAX_BACKOFF_SECONDS)
        }
    }
}
