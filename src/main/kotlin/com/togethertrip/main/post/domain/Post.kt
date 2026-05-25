package com.togethertrip.main.post.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * 여행방 게시글. 거래 기반 기록(transaction 연결) 또는 일반 여행 기록 모두 지원한다.
 */
@Entity
@Table(name = "posts")
class Post(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    // NULL 이면 일반 여행 기록, 값이 있으면 거래 기반 기록
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    var transaction: Transaction? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_participant_id", nullable = false)
    var author: TripParticipant,

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 20)
    var postType: PostType,

    @Column(length = 100)
    var title: String? = null,

    @Column(length = 30)
    var category: String? = null,

    @Column(columnDefinition = "TEXT")
    var content: String? = null,

    @Column(name = "occurred_at")
    var occurredAt: Instant? = null,

    @Column(name = "place_name", length = 100)
    var placeName: String? = null,

    @Column(precision = 10, scale = 7)
    var latitude: BigDecimal? = null,

    @Column(precision = 10, scale = 7)
    var longitude: BigDecimal? = null,

    @Column(name = "comment_count", nullable = false)
    var commentCount: Int = 0,

) : BaseEntity()
