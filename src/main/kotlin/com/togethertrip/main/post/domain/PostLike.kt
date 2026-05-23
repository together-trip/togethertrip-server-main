package com.togethertrip.main.post.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.domain.TripParticipant
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 여행 게시글 좋아요.
 * (post_id, trip_participant_id) 부분 유니크로 1인 1좋아요를 강제한다.
 * created_at / updated_at / deleted_at 은 BaseEntity 에서 제공한다.
 */
@Entity
@Table(
    name = "post_likes",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_post_likes_post_participant",
            columnNames = ["post_id", "trip_participant_id"],
        ),
    ],
)
class PostLike(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    var post: Post,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_participant_id", nullable = false)
    var tripParticipant: TripParticipant,

) : BaseEntity()
