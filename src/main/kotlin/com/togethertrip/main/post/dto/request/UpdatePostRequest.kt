package com.togethertrip.main.post.dto.request

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
    val attachments: List<PostAttachmentRequest>? = null,
)
