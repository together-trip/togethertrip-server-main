package com.togethertrip.main.user.service

import com.togethertrip.main.user.domain.UserAccountDeletionCleanupErrorCode
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupStatus
import com.togethertrip.main.user.repository.UserAccountDeletionCleanupTaskRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class UserAccountDeletionCleanupTaskLifecycleService(
    private val taskRepository: UserAccountDeletionCleanupTaskRepository,
    @Value("\${user.account-deletion-cleanup.lease-duration:PT5M}")
    private val leaseDuration: Duration = Duration.ofMinutes(5),
    private val clock: Clock = Clock.systemUTC(),
) {

    init {
        require(!leaseDuration.isZero && !leaseDuration.isNegative) {
            "Account deletion cleanup lease duration must be positive"
        }
    }

    @Transactional
    fun claimDue(limit: Int): List<UserAccountDeletionCleanupClaim> {
        val now = Instant.now(clock)
        return taskRepository
            .findClaimableForUpdate(now, limit)
            .map { task ->
                val claimId = UUID.randomUUID().toString()
                task.markProcessing(
                    newClaimId = claimId,
                    newLeaseExpiresAt = now.plus(leaseDuration),
                    now = now,
                )
                UserAccountDeletionCleanupClaim(
                    taskId = task.id,
                    claimId = claimId,
                    userId = task.userId,
                    type = task.type,
                    payload = task.payload,
                )
            }
    }

    @Transactional
    fun complete(claim: UserAccountDeletionCleanupClaim): Boolean {
        val task = findCurrentClaim(claim) ?: return false
        task.markCompleted(Instant.now(clock))
        return true
    }

    @Transactional
    fun fail(
        claim: UserAccountDeletionCleanupClaim,
        errorCode: UserAccountDeletionCleanupErrorCode,
    ): Boolean {
        val task = findCurrentClaim(claim) ?: return false
        task.markFailed(errorCode, Instant.now(clock))
        return true
    }

    private fun findCurrentClaim(
        claim: UserAccountDeletionCleanupClaim,
    ) = taskRepository.findByIdAndStatusAndClaimId(
        id = claim.taskId,
        status = UserAccountDeletionCleanupStatus.PROCESSING,
        claimId = claim.claimId,
    )
}
