package com.togethertrip.main.post.dto.request

import com.togethertrip.main.post.validation.ValidPlace
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.time.Instant

@ValidPlace
data class UpdatePostRequest(
    val title: String? = null,
    val category: String? = null,
    val content: String? = null,
    val occurredAt: Instant? = null,
    override val placeName: String? = null,
    override val latitude: BigDecimal? = null,
    override val longitude: BigDecimal? = null,
    val replaceAttachments: Boolean = false,
    val files: List<MultipartFile> = emptyList(),
) : PlaceRequest
