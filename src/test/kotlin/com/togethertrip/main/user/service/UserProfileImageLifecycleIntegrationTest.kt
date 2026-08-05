package com.togethertrip.main.user.service

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.dto.request.UpdateUserRequest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@MainIntegrationTest
class UserProfileImageLifecycleIntegrationTest @Autowired constructor(
    private val userService: UserService,
    private val entityManager: EntityManager,
    private val jdbcTemplate: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
) {

    @Test
    fun `프로필 교체가 커밋되면 이전 managed 파일 정리 작업도 함께 저장된다`() {
        val previousUrl = "/uploads/user-profile-images/previous-commit.jpg"
        val nextUrl = "https://k.kakaocdn.net/next-commit.jpg"
        val userId = persistUser("교체커밋-${System.nanoTime()}", previousUrl)

        transactionTemplate.executeWithoutResult {
            userService.updateMe(
                userId = userId,
                request = UpdateUserRequest(profileImageUrl = nextUrl),
            )
        }

        assertEquals(nextUrl, findProfileImageUrl(userId))
        assertEquals(listOf(previousUrl), findPendingProfileImageCleanupPayloads(userId))
    }

    @Test
    fun `프로필 교체가 롤백되면 기존 URL을 보존하고 정리 작업도 남기지 않는다`() {
        val previousUrl = "/uploads/user-profile-images/previous-rollback.jpg"
        val nextUrl = "https://k.kakaocdn.net/next-rollback.jpg"
        val userId = persistUser("교체롤백-${System.nanoTime()}", previousUrl)

        transactionTemplate.executeWithoutResult { status ->
            userService.updateMe(
                userId = userId,
                request = UpdateUserRequest(profileImageUrl = nextUrl),
            )
            status.setRollbackOnly()
        }

        assertEquals(previousUrl, findProfileImageUrl(userId))
        assertEquals(emptyList(), findPendingProfileImageCleanupPayloads(userId))
    }

    @Test
    fun `다른 managed 프로필 이미지 URL은 직접 재지정할 수 없다`() {
        val currentUrl = "/uploads/user-profile-images/current.jpg"
        val previousUrl = "/uploads/user-profile-images/previous.jpg"
        val userId = persistUser("재지정거부-${System.nanoTime()}", currentUrl)

        val exception = assertFailsWith<BusinessException> {
            transactionTemplate.executeWithoutResult {
                userService.updateMe(
                    userId = userId,
                    request = UpdateUserRequest(profileImageUrl = previousUrl),
                )
            }
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
        assertEquals(currentUrl, findProfileImageUrl(userId))
        assertEquals(emptyList(), findPendingProfileImageCleanupPayloads(userId))
    }

    private fun persistUser(
        nickname: String,
        profileImageUrl: String,
    ): Long {
        return requireNotNull(
            transactionTemplate.execute {
                val user = User(
                    nickname = nickname,
                    profileImageUrl = profileImageUrl,
                )
                entityManager.persist(user)
                entityManager.flush()
                user.id
            }
        )
    }

    private fun findProfileImageUrl(userId: Long): String? {
        return jdbcTemplate.queryForObject(
            "select profile_image_url from users where id = ?",
            String::class.java,
            userId,
        )
    }

    private fun findPendingProfileImageCleanupPayloads(userId: Long): List<String> {
        return jdbcTemplate.queryForList(
            """
                select payload
                from user_account_deletion_cleanup_tasks
                where user_id = ?
                  and task_type = 'PROFILE_IMAGE'
                  and status = 'PENDING'
                order by id
            """.trimIndent(),
            String::class.java,
            userId,
        )
    }
}
