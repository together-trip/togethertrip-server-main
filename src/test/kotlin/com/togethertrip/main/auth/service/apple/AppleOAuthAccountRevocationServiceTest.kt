package com.togethertrip.main.auth.service.apple

import com.togethertrip.main.auth.client.AppleOAuthClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AppleOAuthAccountRevocationServiceTest {
    @Test
    fun `Apple 계정 refresh token을 복호화해 revoke한다`() {
        val client = mock(AppleOAuthClient::class.java)
        val cipher = mock(AppleTokenCipher::class.java)
        `when`(cipher.decrypt("encrypted-token")).thenReturn("apple-refresh-token")
        val service = AppleOAuthAccountRevocationService(client, cipher)

        service.revokeEncrypted("encrypted-token")

        verify(client).revoke("apple-refresh-token")
    }
}
