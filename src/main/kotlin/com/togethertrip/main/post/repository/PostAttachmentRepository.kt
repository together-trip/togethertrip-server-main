package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.PostAttachment
import org.springframework.data.jpa.repository.JpaRepository

interface PostAttachmentRepository : JpaRepository<PostAttachment, Long> {

    fun findByIdAndPostIdAndDeletedAtIsNull(id: Long, postId: Long): PostAttachment?

    fun findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(postId: Long): List<PostAttachment>

    fun findByPostIdInAndDeletedAtIsNullOrderByPostIdAscSortOrderAsc(postIds: Collection<Long>): List<PostAttachment>
}
