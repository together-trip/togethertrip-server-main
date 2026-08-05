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
    }
}
