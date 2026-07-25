package com.togethertrip.main.triprecap.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.user.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

@Entity
@Table(name = "trip_recaps")
@SQLRestriction("deleted_at IS NULL")
class TripRecap(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    var requestedBy: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var style: TripRecapStyle,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TripRecapStatus = TripRecapStatus.CREATING,

    @Column(name = "scene_count", nullable = false)
    var sceneCount: Int = 0,

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 1,

    @Column(name = "failure_reason", length = 1000)
    var failureReason: String? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "moderation_hidden_at")
    var moderationHiddenAt: Instant? = null,

    @Column(name = "moderation_deleted_at")
    var moderationDeletedAt: Instant? = null,

) : BaseEntity() {

    fun retry(
        style: TripRecapStyle,
        requestedBy: User,
        now: Instant,
    ) {
        this.style = style
        this.requestedBy = requestedBy
        status = TripRecapStatus.CREATING
        sceneCount = 0
        attemptCount += 1
        failureReason = null
        completedAt = null
        updatedAt = now
    }

    fun complete(
        sceneCount: Int,
        now: Instant,
    ) {
        status = TripRecapStatus.COMPLETED
        this.sceneCount = sceneCount
        failureReason = null
        completedAt = now
        updatedAt = now
    }

    fun fail(
        reason: String,
        now: Instant,
    ) {
        status = TripRecapStatus.FAILED
        failureReason = reason.take(MAX_FAILURE_REASON_LENGTH)
        completedAt = null
        updatedAt = now
    }

    fun isCreating(): Boolean {
        return status == TripRecapStatus.CREATING
    }

    fun isFailed(): Boolean {
        return status == TripRecapStatus.FAILED
    }

    fun hideByModeration(now: Instant) {
        moderationHiddenAt = now
        updatedAt = now
    }

    fun deleteByModeration(now: Instant) {
        moderationDeletedAt = now
        moderationHiddenAt = now
        updatedAt = now
    }

    fun isVisibleByModeration(): Boolean = moderationHiddenAt == null && moderationDeletedAt == null

    companion object {
        private const val MAX_FAILURE_REASON_LENGTH = 1000
    }
}
