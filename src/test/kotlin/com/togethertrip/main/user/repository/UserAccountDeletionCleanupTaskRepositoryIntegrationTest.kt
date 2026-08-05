package com.togethertrip.main.user.repository

import com.togethertrip.main.auth.service.RefreshTokenService
import com.togethertrip.main.auth.service.apple.OAuthAccountRevoker
import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupStatus
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupTask
import com.togethertrip.main.user.service.UserAccountDeletionCleanupDispatchService
import com.togethertrip.main.user.service.UserAccountDeletionCleanupTaskLifecycleService
import com.togethertrip.main.user.service.storage.UserProfileImageStorage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@MainIntegrationTest
class UserAccountDeletionCleanupTaskRepositoryIntegrationTest @Autowired constructor(
    private val repository: UserAccountDeletionCleanupTaskRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    @Test
    fun `프로필 파일 삭제 실패는 백오프 후 재시도하고 완료 뒤 다시 실행하지 않는다`() {
        val startedAt = Instant.parse("2026-08-05T00:00:00Z")
        val profileImageUrl = "/uploads/user-profile-images/previous.jpg"
        val taskId = inTransaction {
            repository.saveAndFlush(
                UserAccountDeletionCleanupTask.profileImage(
                    userId = System.nanoTime(),
                    profileImageUrl = profileImageUrl,
                    now = startedAt,
                )
            ).id
        }
        val profileImageStorage = mock(UserProfileImageStorage::class.java)
        doThrow(IllegalStateException("temporary storage failure"))
            .doNothing()
            .`when`(profileImageStorage).deleteByFileUrl(profileImageUrl)

        val failed = inTransaction {
            createService(profileImageStorage, startedAt).dispatchDue(limit = 1)
        }
        assertEquals(1, failed.failedCount)
        assertEquals(
            0,
            inTransaction {
                createService(profileImageStorage, startedAt.plusSeconds(29))
                    .dispatchDue(limit = 1)
                    .requestedCount
            },
        )

        val recovered = inTransaction {
            createService(profileImageStorage, startedAt.plusSeconds(30))
                .dispatchDue(limit = 1)
        }
        val afterCompletion = inTransaction {
            createService(profileImageStorage, startedAt.plusSeconds(31))
                .dispatchDue(limit = 1)
        }

        assertEquals(1, recovered.completedCount)
        assertEquals(0, afterCompletion.requestedCount)
        verify(profileImageStorage, times(2)).deleteByFileUrl(profileImageUrl)
        inTransaction {
            val task = repository.findById(taskId).orElseThrow()
            assertEquals(UserAccountDeletionCleanupStatus.COMPLETED, task.status)
            assertEquals(1, task.retryCount)
            assertNull(task.payload)
        }
    }

    @Test
    fun `실패 작업은 백오프 만료 후 다시 claim되어 완료된다`() {
        val startedAt = Instant.parse("2026-08-05T01:00:00Z")
        val taskId = inTransaction {
            repository.saveAndFlush(
                UserAccountDeletionCleanupTask.appleRefreshToken(
                    userId = System.nanoTime(),
                    encryptedRefreshToken = "encrypted-token",
                    now = startedAt,
                )
            ).id
        }
        val failingRevoker = OAuthAccountRevoker { throw IllegalStateException("sensitive-token") }

        val failed = inTransaction {
            createService(failingRevoker, startedAt).dispatchDue(limit = 1)
        }
        assertEquals(1, failed.failedCount)

        val beforeDue = inTransaction {
            createService(mock(OAuthAccountRevoker::class.java), startedAt.plusSeconds(29))
                .dispatchDue(limit = 1)
        }
        assertEquals(0, beforeDue.requestedCount)

        val recoveringRevoker = mock(OAuthAccountRevoker::class.java)
        val recovered = inTransaction {
            createService(recoveringRevoker, startedAt.plusSeconds(30)).dispatchDue(limit = 1)
        }

        assertEquals(1, recovered.completedCount)
        verify(recoveringRevoker).revokeEncrypted("encrypted-token")
        inTransaction {
            val task = repository.findById(taskId).orElseThrow()
            assertEquals(UserAccountDeletionCleanupStatus.COMPLETED, task.status)
            assertEquals(1, task.retryCount)
            assertNull(task.payload)
            assertNull(task.lastErrorCode)
        }
    }

    @Test
    fun `두 worker가 동시에 due 작업을 조회해도 claim한 ID는 겹치지 않는다`() {
        val now = Instant.parse("2026-08-05T02:00:00Z")
        val taskIds = inTransaction {
            repository.saveAllAndFlush(
                (0 until 10).map { index ->
                    UserAccountDeletionCleanupTask.refreshToken(
                        userId = System.nanoTime() + index,
                        now = now,
                    )
                }
            ).map { it.id }.toSet()
        }
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val selected = CountDownLatch(2)

        try {
            val futures = (0 until 2).map {
                executor.submit<Set<Long>> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    inTransaction {
                        val tasks = repository.findClaimableForUpdate(now, 10)
                        selected.countDown()
                        selected.await(2, TimeUnit.SECONDS)
                        tasks.forEach { task ->
                            task.markProcessing(
                                newClaimId = UUID.randomUUID().toString(),
                                newLeaseExpiresAt = now.plusSeconds(60),
                                now = now,
                            )
                        }
                        tasks.map { task -> task.id }.toSet()
                    }
                }
            }
            assertEquals(true, ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val first = futures[0].get(10, TimeUnit.SECONDS)
            val second = futures[1].get(10, TimeUnit.SECONDS)
            assertEquals(emptySet(), first intersect second)
            assertEquals(taskIds, first union second)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `worker lease가 만료된 processing 작업은 새 claim으로 복구한다`() {
        val startedAt = Instant.parse("2026-08-05T03:00:00Z")
        val taskId = inTransaction {
            repository.saveAndFlush(
                UserAccountDeletionCleanupTask.refreshToken(System.nanoTime(), startedAt)
                    .apply {
                        markProcessing("expired-claim", startedAt.plusSeconds(60), startedAt)
                    }
            ).id
        }

        val beforeExpiry = createLifecycle(startedAt.plusSeconds(59))
        assertEquals(0, inTransaction { beforeExpiry.claimDue(1) }.size)

        val afterExpiry = createLifecycle(startedAt.plusSeconds(60))
        val recovered = inTransaction { afterExpiry.claimDue(1) }

        assertEquals(1, recovered.size)
        assertEquals(taskId, recovered.single().taskId)
        inTransaction {
            val task = repository.findById(taskId).orElseThrow()
            assertEquals(UserAccountDeletionCleanupStatus.PROCESSING, task.status)
            assertEquals(1, task.retryCount)
            assertEquals("WORKER_LEASE_EXPIRED", task.lastErrorCode?.name)
        }
    }

    private fun createService(
        revoker: OAuthAccountRevoker,
        now: Instant,
    ): UserAccountDeletionCleanupDispatchService {
        return UserAccountDeletionCleanupDispatchService(
            taskLifecycleService = createLifecycle(now),
            oauthAccountRevoker = revoker,
            profileImageStorage = mock(UserProfileImageStorage::class.java),
            refreshTokenService = mock(RefreshTokenService::class.java),
            userRepository = mock(UserRepository::class.java),
        )
    }

    private fun createService(
        profileImageStorage: UserProfileImageStorage,
        now: Instant,
    ): UserAccountDeletionCleanupDispatchService {
        return UserAccountDeletionCleanupDispatchService(
            taskLifecycleService = createLifecycle(now),
            oauthAccountRevoker = mock(OAuthAccountRevoker::class.java),
            profileImageStorage = profileImageStorage,
            refreshTokenService = mock(RefreshTokenService::class.java),
            userRepository = mock(UserRepository::class.java),
        )
    }

    private fun createLifecycle(now: Instant): UserAccountDeletionCleanupTaskLifecycleService {
        return UserAccountDeletionCleanupTaskLifecycleService(
            taskRepository = repository,
            leaseDuration = Duration.ofMinutes(1),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    private fun <T> inTransaction(block: () -> T): T {
        return requireNotNull(transactionTemplate.execute { block() })
    }
}
