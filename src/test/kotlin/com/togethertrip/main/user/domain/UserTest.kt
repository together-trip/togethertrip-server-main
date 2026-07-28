package com.togethertrip.main.user.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserTest {

    @Test
    fun `닉네임이 있으면 프로필 완료 상태다`() {
        val user = User(nickname = "여행자")

        assertTrue(user.isProfileCompleted())
    }

    @Test
    fun `닉네임이 비어 있으면 프로필 미완료 상태다`() {
        val user = User(nickname = " ")

        assertFalse(user.isProfileCompleted())
    }

    @Test
    fun `회원 탈퇴 시 직접 식별 프로필과 제재 정보를 익명화한다`() {
        val deletedAt = Instant.parse("2026-07-28T12:00:00Z")
        val user = User(
            nickname = "여행자",
            gender = "FEMALE",
            birthDate = LocalDate.of(1995, 5, 1),
            profileImageUrl = "/uploads/user-profile-images/profile.jpg",
        ).apply {
            restrictModeration("신고 누적", deletedAt.plusSeconds(3600), deletedAt.minusSeconds(60))
        }

        user.anonymizeAndWithdraw(deletedAt)

        assertEquals(User.WITHDRAWN_USER_NICKNAME, user.nickname)
        assertNull(user.gender)
        assertNull(user.birthDate)
        assertNull(user.profileImageUrl)
        assertNull(user.moderationRestrictedAt)
        assertNull(user.moderationRestrictedUntil)
        assertNull(user.moderationRestrictionReason)
        assertEquals(UserStatus.WITHDRAWN, user.status)
        assertNotNull(user.deletedAt)
        assertEquals(deletedAt, user.deletedAt)
    }
}
