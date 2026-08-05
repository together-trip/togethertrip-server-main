package com.togethertrip.main.user.service

import com.togethertrip.main.auth.domain.OAuthAccount
import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.repository.OAuthAccountRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupTask
import com.togethertrip.main.user.domain.UserAccountDeletionCleanupType
import com.togethertrip.main.user.repository.UserAccountDeletionCleanupTaskRepository
import com.togethertrip.main.user.service.storage.UserProfileImageStorage
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class UserAccountDeletionCleanupEnqueueServiceTest {

    @Test
    fun `Apple 토큰과 프로필 이미지와 Redis 정리 작업을 삭제 트랜잭션에 저장한다`() {
        val oauthAccountRepository = mock(OAuthAccountRepository::class.java)
        val taskRepository = mock(UserAccountDeletionCleanupTaskRepository::class.java)
        val profileImageStorage = mock(UserProfileImageStorage::class.java)
        val user = User(nickname = "여행자").apply { id = 1L }
        `when`(oauthAccountRepository.findAllByUserIdAndProvider(1L, OAuthProvider.APPLE))
            .thenReturn(
                listOf(
                    OAuthAccount(
                        user = user,
                        provider = OAuthProvider.APPLE,
                        providerUserId = "apple-1",
                        encryptedRefreshToken = "encrypted-1",
                    ),
                    OAuthAccount(
                        user = user,
                        provider = OAuthProvider.APPLE,
                        providerUserId = "apple-2",
                        encryptedRefreshToken = null,
                    ),
                )
            )
        `when`(taskRepository.saveAll(anyList<UserAccountDeletionCleanupTask>()))
            .thenAnswer { it.getArgument(0) }
        `when`(profileImageStorage.isManagedFileUrl("/uploads/user-profile-images/profile.jpg"))
            .thenReturn(true)
        val service = UserAccountDeletionCleanupEnqueueService(
            oauthAccountRepository = oauthAccountRepository,
            taskRepository = taskRepository,
            profileImageStorage = profileImageStorage,
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )

        service.enqueue(
            userId = 1L,
            profileImageUrl = "/uploads/user-profile-images/profile.jpg",
        )

        @Suppress("UNCHECKED_CAST")
        val tasks = mockingDetails(taskRepository).invocations
            .single { it.method.name == "saveAll" }
            .arguments[0] as List<UserAccountDeletionCleanupTask>
        assertEquals(
            listOf(
                UserAccountDeletionCleanupType.APPLE_REFRESH_TOKEN,
                UserAccountDeletionCleanupType.PROFILE_IMAGE,
                UserAccountDeletionCleanupType.REDIS_REFRESH_TOKEN,
            ),
            tasks.map { it.type },
        )
        assertEquals("encrypted-1", tasks[0].payload)
        assertEquals("/uploads/user-profile-images/profile.jpg", tasks[1].payload)
    }

    @Test
    fun `관리하지 않는 외부 프로필 URL은 정리 작업 payload로 복제하지 않는다`() {
        val oauthAccountRepository = mock(OAuthAccountRepository::class.java)
        val taskRepository = mock(UserAccountDeletionCleanupTaskRepository::class.java)
        val profileImageStorage = mock(UserProfileImageStorage::class.java)
        `when`(oauthAccountRepository.findAllByUserIdAndProvider(1L, OAuthProvider.APPLE))
            .thenReturn(emptyList())
        `when`(taskRepository.saveAll(anyList<UserAccountDeletionCleanupTask>()))
            .thenAnswer { it.getArgument(0) }
        val service = UserAccountDeletionCleanupEnqueueService(
            oauthAccountRepository = oauthAccountRepository,
            taskRepository = taskRepository,
            profileImageStorage = profileImageStorage,
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )

        service.enqueue(1L, "https://k.kakaocdn.net/profile.jpg")

        @Suppress("UNCHECKED_CAST")
        val tasks = mockingDetails(taskRepository).invocations
            .single { it.method.name == "saveAll" }
            .arguments[0] as List<UserAccountDeletionCleanupTask>
        assertEquals(listOf(UserAccountDeletionCleanupType.REDIS_REFRESH_TOKEN), tasks.map { it.type })
        assertEquals(listOf(null), tasks.map { it.payload })
    }
}
