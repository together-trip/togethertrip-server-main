package com.togethertrip.main.post.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.domain.TripParticipant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * 여행 게시글 댓글.
 */
@Entity
@Table(name = "post_comments")
class PostComment(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    var post: Post,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_participant_id", nullable = false)
    var author: TripParticipant,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

) : BaseEntity()
