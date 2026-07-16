package com.togethertrip.main.post.dto.request

import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.validation.ValidPlace
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.time.Instant

/**
 * 게시글 작성 요청.
 * transactionId 가 있으면 거래 기반 기록, 없으면 일반 여행 기록으로 생성한다.
 */
@ValidPlace
data class CreatePostRequest(
    val transactionId: Long? = null,
    val title: String? = null,
    val category: String? = null,
    val content: String? = null,
    val postType: PostType = PostType.RECORD,
    val occurredAt: Instant? = null,
    override val placeName: String? = null,
    override val latitude: BigDecimal? = null,
    override val longitude: BigDecimal? = null,
    val files: List<MultipartFile> = emptyList(),
) : PlaceRequest
