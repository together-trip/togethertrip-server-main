package com.togethertrip.main.user.service

import com.togethertrip.main.auth.service.RefreshTokenService
import com.togethertrip.main.auth.service.apple.OAuthAccountRevoker
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupErrorCode
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupType
import com.togethertrip.main.user.service.storage.UserProfileImageStorage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals

class UserAccountDeletionCleanupDispatchServiceTest {

    private val taskLifecycleService = mock(UserAccountDeletionCleanupTaskLifecycleService::class.java)
    private val oauthAccountRevoker = mock(OAuthAccountRevoker::class.java)
    private val profileImageStorage = mock(UserProfileImageStorage::class.java)
    private val refreshTokenService = mock(RefreshTokenService::class.java)
    private val service = UserAccountDeletionCleanupDispatchService(
        taskLifecycleService = taskLifecycleService,
        oauthAccountRevoker = oauthAccountRevoker,
        profileImageStorage = profileImageStorage,
        refreshTokenService = refreshTokenService,
    )

    @Test
    fun `claim 트랜잭션 밖에서 서로 다른 외부 정리 작업을 실행하고 개별 완료한다`() {
        val appleClaim = claim(1L, UserAccountDeletionCleanupType.APPLE_REFRESH_TOKEN, "encrypted")
        val profileClaim = claim(2L, UserAccountDeletionCleanupType.PROFILE_IMAGE, "/uploads/profile.jpg")
        val refreshClaim = claim(3L, UserAccountDeletionCleanupType.REDIS_REFRESH_TOKEN, null)
        `when`(taskLifecycleService.claimDue(50))
            .thenReturn(listOf(appleClaim, profileClaim, refreshClaim))
        listOf(appleClaim, profileClaim, refreshClaim).forEach { claimedTask ->
            `when`(taskLifecycleService.complete(claimedTask)).thenReturn(true)
        }

        val result = service.dispatchDue()

        verify(oauthAccountRevoker).revokeEncrypted("encrypted")
        verify(profileImageStorage).deleteByFileUrl("/uploads/profile.jpg")
        verify(refreshTokenService).delete(1L)
        verify(taskLifecycleService).complete(appleClaim)
        verify(taskLifecycleService).complete(profileClaim)
        verify(taskLifecycleService).complete(refreshClaim)
        assertEquals(3, result.completedCount)
        assertEquals(0, result.failedCount)
    }

    @Test
    fun `한 작업 실패가 나머지 정리를 막지 않고 비민감 오류 코드만 저장한다`() {
        val appleClaim = claim(1L, UserAccountDeletionCleanupType.APPLE_REFRESH_TOKEN, "encrypted")
        val refreshClaim = claim(2L, UserAccountDeletionCleanupType.REDIS_REFRESH_TOKEN, null)
        `when`(taskLifecycleService.claimDue(50)).thenReturn(listOf(appleClaim, refreshClaim))
        `when`(taskLifecycleService.fail(
            appleClaim,
            UserAccountDeletionCleanupErrorCode.APPLE_TOKEN_REVOCATION_FAILED,
        )).thenReturn(true)
        `when`(taskLifecycleService.complete(refreshClaim)).thenReturn(true)
        doThrow(IllegalStateException("secret encrypted profile-url"))
            .`when`(oauthAccountRevoker).revokeEncrypted("encrypted")

        val result = service.dispatchDue()

        verify(taskLifecycleService).fail(
            appleClaim,
            UserAccountDeletionCleanupErrorCode.APPLE_TOKEN_REVOCATION_FAILED,
        )
        verify(refreshTokenService).delete(1L)
        assertEquals(1, result.completedCount)
        assertEquals(1, result.failedCount)
    }

    private fun claim(
        taskId: Long,
        type: UserAccountDeletionCleanupType,
        payload: String?,
    ) = UserAccountDeletionCleanupClaim(
        taskId = taskId,
        claimId = "claim-$taskId",
        userId = 1L,
        type = type,
        payload = payload,
    )
}
