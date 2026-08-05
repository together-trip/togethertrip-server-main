package com.togethertrip.main.user.repository

import com.togethertrip.main.user.domain.UserAccountDeletionCleanupTask
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
            where status in ('PENDING', 'FAILED')
              and next_attempt_at <= :now
              and deleted_at is null
            order by next_attempt_at asc, id asc
            limit :limit
            for update skip locked
        """,
        nativeQuery = true,
    )
    fun findDueForUpdate(
        @Param("now") now: Instant,
        @Param("limit") limit: Int,
    ): List<UserAccountDeletionCleanupTask>
}
