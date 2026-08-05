package com.togethertrip.main.user.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserAccountDeletionCleanupTaskTest {

    @Test
    fun `정리 성공 시 완료 상태로 바꾸고 민감 payload를 제거한다`() {
        val task = UserAccountDeletionCleanupTask.appleRefreshToken(
            userId = 1L,
            encryptedRefreshToken = "encrypted-token",
            now = Instant.EPOCH,
        )
        val completedAt = Instant.parse("2026-08-05T01:00:00Z")
        task.markProcessing("claim-1", completedAt.plusSeconds(60), completedAt)

        task.markCompleted(completedAt)

        assertEquals(UserAccountDeletionCleanupStatus.COMPLETED, task.status)
        assertNull(task.payload)
        assertEquals(completedAt, task.completedAt)
        assertNull(task.lastErrorCode)
    }

    @Test
    fun `정리 실패 시 재시도 횟수와 지수 백오프 시각을 기록한다`() {
        val task = UserAccountDeletionCleanupTask.refreshToken(
            userId = 1L,
            now = Instant.EPOCH,
        )
        val failedAt = Instant.parse("2026-08-05T01:00:00Z")
        task.markProcessing("claim-1", failedAt.plusSeconds(60), failedAt)

        task.markFailed(
            errorCode = UserAccountDeletionCleanupErrorCode.REDIS_REFRESH_TOKEN_DELETE_FAILED,
            now = failedAt,
        )

        assertEquals(UserAccountDeletionCleanupStatus.FAILED, task.status)
        assertEquals(1, task.retryCount)
        assertEquals(failedAt.plusSeconds(30), task.nextAttemptAt)
        assertEquals(
            UserAccountDeletionCleanupErrorCode.REDIS_REFRESH_TOKEN_DELETE_FAILED,
            task.lastErrorCode,
        )
        assertNull(task.claimId)
        assertNull(task.leaseExpiresAt)
    }

    @Test
    fun `만료된 processing 작업을 다시 claim하면 lease 만료를 추적한다`() {
        val task = UserAccountDeletionCleanupTask.refreshToken(1L, Instant.EPOCH)
        task.markProcessing("claim-1", Instant.EPOCH.plusSeconds(60), Instant.EPOCH)

        task.markProcessing(
            newClaimId = "claim-2",
            newLeaseExpiresAt = Instant.EPOCH.plusSeconds(120),
            now = Instant.EPOCH.plusSeconds(60),
        )

        assertEquals(UserAccountDeletionCleanupStatus.PROCESSING, task.status)
        assertEquals(1, task.retryCount)
        assertEquals(UserAccountDeletionCleanupErrorCode.WORKER_LEASE_EXPIRED, task.lastErrorCode)
        assertEquals("claim-2", task.claimId)
    }
}
