package com.togethertrip.main.post.domain

import com.togethertrip.main.global.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * 여행 게시글 첨부 파일.
 * created_at / updated_at / deleted_at(soft delete) 은 BaseEntity 에서 제공한다.
 */
@Entity
@Table(name = "post_attachments")
class PostAttachment(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    var post: Post,

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false, length = 20)
    var attachmentType: PostAttachmentType,

    @Column(name = "file_url", nullable = false, length = 1000)
    var fileUrl: String,

    @Column(name = "thumbnail_url", length = 1000)
    var thumbnailUrl: String? = null,

    @Column(name = "file_size")
    var fileSize: Long? = null,

    @Column(name = "mime_type", length = 100)
    var mimeType: String? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

) : BaseEntity()
