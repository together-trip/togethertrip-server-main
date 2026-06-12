package com.togethertrip.main.global.storage

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileImageUrlPolicyTest {

    private val policy = ProfileImageUrlPolicy(
        userProfileImagePublicUrlPrefix = "/uploads/user-profile-images",
    )

    @Test
    fun `서버 업로드 프로필 이미지 URL은 허용한다`() {
        assertTrue(policy.isAllowed("/uploads/user-profile-images/profile.jpg"))
        assertTrue(policy.isAllowed("/uploads/user-profile-images/profile-01.png"))
    }

    @Test
    fun `서버 업로드 프로필 이미지 URL은 단일 파일명 형식만 허용한다`() {
        assertFalse(policy.isAllowed("/uploads/user-profile-images/nested/profile.jpg"))
        assertFalse(policy.isAllowed("/uploads/user-profile-images/../profile.jpg"))
        assertFalse(policy.isAllowed("/uploads/user-profile-images/%2e%2e/profile.jpg"))
        assertFalse(policy.isAllowed("/uploads/user-profile-images/profile.svg"))
        assertFalse(policy.isAllowed("/uploads/user-profile-images/profile.jpg?x=1"))
    }

    @Test
    fun `https 카카오 CDN 이미지 URL은 허용한다`() {
        assertTrue(policy.isAllowed("https://k.kakaocdn.net/profile.jpg"))
        assertTrue(policy.isAllowed("https://img1.kakaocdn.net/profile.jpg"))
    }

    @Test
    fun `javascript와 data URL은 거부한다`() {
        assertFalse(policy.isAllowed("javascript:alert(1)"))
        assertFalse(policy.isAllowed("data:image/svg+xml,<svg></svg>"))
    }

    @Test
    fun `신뢰하지 않는 외부 URL은 거부한다`() {
        assertFalse(policy.isAllowed("https://example.com/profile.jpg"))
        assertFalse(policy.isAllowed("https://kakaocdn.net.evil.example/profile.jpg"))
    }

    @Test
    fun `허용되지 않는 URL은 sanitize에서 null로 정리한다`() {
        assertNull(policy.sanitize("https://example.com/profile.jpg"))
        assertEquals(
            "https://k.kakaocdn.net/profile.jpg",
            policy.sanitize(" https://k.kakaocdn.net/profile.jpg "),
        )
    }
}
