package com.togethertrip.main.user.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `탈퇴 사용자를 재가입 상태로 재활성화한다`() {
        val user = User(nickname = "여행자").apply { withdraw() }

        user.reactivateForSignup()

        assertEquals(UserStatus.ACTIVE, user.status)
        assertNull(user.deletedAt)
    }
}
