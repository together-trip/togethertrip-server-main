package com.togethertrip.main.auth.service.apple

import com.togethertrip.main.auth.client.AppleOAuthClient
import com.togethertrip.main.auth.domain.OAuthAccount
import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.repository.OAuthAccountRepository
import com.togethertrip.main.user.domain.User
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AppleOAuthAccountRevocationServiceTest {
    @Test
    fun `Apple 계정 refresh token을 복호화해 revoke한다`() {
        val repository = mock(OAuthAccountRepository::class.java)
        val client = mock(AppleOAuthClient::class.java)
        val cipher = mock(AppleTokenCipher::class.java)
        val account = OAuthAccount(
            user = User(nickname = "여행자").apply { id = 1L },
            provider = OAuthProvider.APPLE,
            providerUserId = "apple-user",
            encryptedRefreshToken = "encrypted-token",
        )
        `when`(repository.findAllByUserIdAndProvider(1L, OAuthProvider.APPLE))
            .thenReturn(listOf(account))
        `when`(cipher.decrypt("encrypted-token")).thenReturn("apple-refresh-token")
        val service = AppleOAuthAccountRevocationService(repository, client, cipher)

        service.revokeForUser(1L)

        verify(client).revoke("apple-refresh-token")
    }
}
