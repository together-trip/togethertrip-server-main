package com.togethertrip.main.user.service.storage

import org.springframework.web.multipart.MultipartFile

interface UserProfileImageStorage {

    fun store(file: MultipartFile): StoredUserProfileImage

    fun delete(storedImage: StoredUserProfileImage)

    fun deleteByFileUrl(fileUrl: String)
}

data class StoredUserProfileImage(
    val storageKey: String,
    val fileUrl: String,
    val fileSize: Long?,
    val mimeType: String?,
)
