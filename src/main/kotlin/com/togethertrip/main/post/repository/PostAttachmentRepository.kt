package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.PostAttachment
import org.springframework.data.jpa.repository.JpaRepository

interface PostAttachmentRepository : JpaRepository<PostAttachment, Long> {

    fun findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(postId: Long): List<PostAttachment>
}
