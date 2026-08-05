package com.togethertrip.main.post.dto.request

import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.validation.ValidPlace
import jakarta.validation.constraints.Size
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
    @field:Size(max = 100)
    val title: String? = null,
    @field:Size(max = 30)
    val category: String? = null,
    @field:Size(max = 5000)
    val content: String? = null,
    val postType: PostType = PostType.RECORD,
    val occurredAt: Instant? = null,
    @field:Size(max = 100)
    override val placeName: String? = null,
    override val latitude: BigDecimal? = null,
    override val longitude: BigDecimal? = null,
    @field:Size(max = 10)
    val files: List<MultipartFile> = emptyList(),
) : PlaceRequest
