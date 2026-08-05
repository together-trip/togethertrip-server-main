package com.togethertrip.main.user.service

import com.togethertrip.main.auth.service.RefreshTokenService
import com.togethertrip.main.auth.service.apple.OAuthAccountRevoker
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupErrorCode
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupStatus
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupTask
import com.togethertrip.main.user.repository.UserAccountDeletionCleanupTaskRepository
import com.togethertrip.main.user.service.storage.UserProfileImageStorage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserAccountDeletionCleanupDispatchServiceTest {

    private val now = Instant.parse("2026-08-05T01:00:00Z")
    private val taskRepository = mock(UserAccountDeletionCleanupTaskRepository::class.java)
    private val oauthAccountRevoker = mock(OAuthAccountRevoker::class.java)
    private val profileImageStorage = mock(UserProfileImageStorage::class.java)
    private val refreshTokenService = mock(RefreshTokenService::class.java)
    private val service = UserAccountDeletionCleanupDispatchService(
        taskRepository = taskRepository,
        oauthAccountRevoker = oauthAccountRevoker,
        profileImageStorage = profileImageStorage,
        refreshTokenService = refreshTokenService,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `서로 다른 외부 정리 작업을 성공시키고 payload를 제거한다`() {
        val appleTask = UserAccountDeletionCleanupTask.appleRefreshToken(1L, "encrypted", Instant.EPOCH)
        val profileTask = UserAccountDeletionCleanupTask.profileImage(1L, "/uploads/profile.jpg", Instant.EPOCH)
        val refreshTask = UserAccountDeletionCleanupTask.refreshToken(1L, Instant.EPOCH)
        `when`(taskRepository.findDueForUpdate(now, 50))
            .thenReturn(listOf(appleTask, profileTask, refreshTask))

        val result = service.dispatchDue()

        verify(oauthAccountRevoker).revokeEncrypted("encrypted")
        verify(profileImageStorage).deleteByFileUrl("/uploads/profile.jpg")
        verify(refreshTokenService).delete(1L)
        assertEquals(3, result.completedCount)
        assertEquals(0, result.failedCount)
        listOf(appleTask, profileTask, refreshTask).forEach { task ->
            assertEquals(UserAccountDeletionCleanupStatus.COMPLETED, task.status)
            assertNull(task.payload)
        }
    }

    @Test
    fun `한 작업 실패가 나머지 정리를 막지 않고 비민감 오류 코드만 저장한다`() {
        val appleTask = UserAccountDeletionCleanupTask.appleRefreshToken(1L, "encrypted", Instant.EPOCH)
        val refreshTask = UserAccountDeletionCleanupTask.refreshToken(1L, Instant.EPOCH)
        `when`(taskRepository.findDueForUpdate(now, 50)).thenReturn(listOf(appleTask, refreshTask))
        doThrow(IllegalStateException("secret encrypted profile-url"))
            .`when`(oauthAccountRevoker).revokeEncrypted("encrypted")

        val result = service.dispatchDue()

        verify(refreshTokenService).delete(1L)
        assertEquals(1, result.completedCount)
        assertEquals(1, result.failedCount)
        assertEquals(UserAccountDeletionCleanupStatus.FAILED, appleTask.status)
        assertEquals(
            UserAccountDeletionCleanupErrorCode.APPLE_TOKEN_REVOCATION_FAILED,
            appleTask.lastErrorCode,
        )
    }
}
