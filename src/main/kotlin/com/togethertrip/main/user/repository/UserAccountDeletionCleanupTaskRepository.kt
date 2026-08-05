package com.togethertrip.main.user.repository

import com.togethertrip.main.user.domain.UserAccountDeletionCleanupTask
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface UserAccountDeletionCleanupTaskRepository :
    JpaRepository<UserAccountDeletionCleanupTask, Long> {

    @Query(
        value = """
            select *
            from user_account_deletion_cleanup_tasks
            where deleted_at is null
              and (
                (status in ('PENDING', 'FAILED') and next_attempt_at <= :now)
                or (status = 'PROCESSING' and lease_expires_at <= :now)
              )
            order by coalesce(lease_expires_at, next_attempt_at) asc, id asc
            limit :limit
            for update skip locked
        """,
        nativeQuery = true,
    )
    fun findClaimableForUpdate(
        @Param("now") now: Instant,
        @Param("limit") limit: Int,
    ): List<UserAccountDeletionCleanupTask>

    fun findByIdAndStatusAndClaimId(
        id: Long,
        status: UserAccountDeletionCleanupStatus,
        claimId: String,
    ): UserAccountDeletionCleanupTask?
}
