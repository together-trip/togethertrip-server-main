package com.togethertrip.main.user.domain

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserTest {

    @Test
    fun `닉네임 성별 생년월일이 모두 있으면 프로필 완료 상태다`() {
        val user = User(
            nickname = "여행자",
            gender = "MALE",
            birthDate = LocalDate.of(1990, 1, 1),
        )

        assertTrue(user.isProfileCompleted())
    }

    @Test
    fun `성별 또는 생년월일이 없으면 프로필 미완료 상태다`() {
        val user = User(nickname = "여행자")

        assertFalse(user.isProfileCompleted())
    }
}
