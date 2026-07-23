package com.togethertrip.main.global.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI

@Component
class ProfileImageUrlPolicy(
    @Value("\${user.profile-images.public-url-prefix}")
    private val userProfileImagePublicUrlPrefix: String,
) {

    fun isAllowed(profileImageUrl: String): Boolean {
        return isUploadedProfileImageUrl(profileImageUrl) ||
            isTrustedExternalImageUrl(profileImageUrl)
    }

    fun sanitize(profileImageUrl: String?): String? {
        return profileImageUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf(::isAllowed)
    }

    private fun isUploadedProfileImageUrl(profileImageUrl: String): Boolean {
        val normalizedPrefix = userProfileImagePublicUrlPrefix.trimEnd('/')
        val escapedPrefix = Regex.escape(normalizedPrefix)

        return Regex("$escapedPrefix/[A-Za-z0-9][A-Za-z0-9._-]*\\.(jpg|jpeg|png)").matches(profileImageUrl)
    }

    private fun isTrustedExternalImageUrl(profileImageUrl: String): Boolean {
        val uri = runCatching { URI(profileImageUrl) }
            .getOrNull()
            ?: return false

        return uri.scheme == HTTPS_SCHEME &&
            uri.host?.let(::isTrustedKakaoImageHost) == true
    }

    private fun isTrustedKakaoImageHost(host: String): Boolean {
        val normalizedHost = host.lowercase()

        return normalizedHost == KAKAO_CDN_HOST ||
            normalizedHost.endsWith(".$KAKAO_CDN_HOST")
    }

    companion object {
        private const val HTTPS_SCHEME = "https"
        private const val KAKAO_CDN_HOST = "kakaocdn.net"
    }
}
