package com.togethertrip.main.post.dto.request

import com.togethertrip.main.post.domain.PostType
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.time.Instant

/**
 * 게시글 작성 요청.
 * transactionId 가 있으면 거래 기반 기록, 없으면 일반 여행 기록으로 생성한다.
 */
data class CreatePostRequest(
    val transactionId: Long? = null,
    val title: String? = null,
    val category: String? = null,
    val content: String? = null,
    val postType: PostType = PostType.RECORD,
    val occurredAt: Instant? = null,
    val placeName: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val files: List<MultipartFile> = emptyList(),
)
