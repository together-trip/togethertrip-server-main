package com.togethertrip.main.user.service

import com.togethertrip.main.user.domain.UserAccountDeletionCleanupErrorCode
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupStatus
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupTask
import com.togethertrip.main.user.repository.UserAccountDeletionCleanupTaskRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserAccountDeletionCleanupTaskLifecycleServiceTest {

    private val now = Instant.parse("2026-08-05T01:00:00Z")
    private val repository = mock(UserAccountDeletionCleanupTaskRepository::class.java)
    private val service = UserAccountDeletionCleanupTaskLifecycleService(
        taskRepository = repository,
        leaseDuration = Duration.ofMinutes(1),
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `due 작업을 짧은 lease로 claim한다`() {
        val task = UserAccountDeletionCleanupTask.appleRefreshToken(1L, "encrypted", Instant.EPOCH)
            .apply { id = 10L }
        `when`(repository.findClaimableForUpdate(now, 20)).thenReturn(listOf(task))

        val claim = service.claimDue(20).single()

        assertEquals(10L, claim.taskId)
        assertEquals("encrypted", claim.payload)
        assertEquals(UserAccountDeletionCleanupStatus.PROCESSING, task.status)
        assertNotNull(task.claimId)
        assertEquals(now.plusSeconds(60), task.leaseExpiresAt)
    }

    @Test
    fun `현재 claim만 완료 또는 실패 상태를 저장할 수 있다`() {
        val task = UserAccountDeletionCleanupTask.refreshToken(1L, Instant.EPOCH)
            .apply {
                id = 10L
                markProcessing("claim-1", now.plusSeconds(60), now)
            }
        val claim = UserAccountDeletionCleanupClaim(
            taskId = 10L,
            claimId = "claim-1",
            userId = 1L,
            type = task.type,
            payload = null,
        )
        `when`(repository.findByIdAndStatusAndClaimId(
            10L,
            UserAccountDeletionCleanupStatus.PROCESSING,
            "claim-1",
        )).thenReturn(task, null)

        assertTrue(service.fail(claim, UserAccountDeletionCleanupErrorCode.REDIS_REFRESH_TOKEN_DELETE_FAILED))
        assertEquals(UserAccountDeletionCleanupStatus.FAILED, task.status)

        assertFalse(service.complete(claim))
    }
}
