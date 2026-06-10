package com.togethertrip.main.post.dto.request

import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.time.Instant

data class UpdatePostRequest(
    val title: String? = null,
    val category: String? = null,
    val content: String? = null,
    val occurredAt: Instant? = null,
    val placeName: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val replaceAttachments: Boolean = false,
    val files: List<MultipartFile> = emptyList(),
)
