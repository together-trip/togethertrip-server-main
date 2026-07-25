package com.togethertrip.main.post.dto.request

import com.togethertrip.main.post.validation.ValidPlace
import jakarta.validation.constraints.Size
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.time.Instant

@ValidPlace
data class UpdatePostRequest(
    @field:Size(max = 100)
    val title: String? = null,
    @field:Size(max = 30)
    val category: String? = null,
    @field:Size(max = 5000)
    val content: String? = null,
    val occurredAt: Instant? = null,
    @field:Size(max = 100)
    override val placeName: String? = null,
    override val latitude: BigDecimal? = null,
    override val longitude: BigDecimal? = null,
    val replaceAttachments: Boolean = false,
    @field:Size(max = 10)
    val files: List<MultipartFile> = emptyList(),
) : PlaceRequest
