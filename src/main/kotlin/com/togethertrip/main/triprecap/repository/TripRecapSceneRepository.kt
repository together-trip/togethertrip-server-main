package com.togethertrip.main.triprecap.repository

import com.togethertrip.main.triprecap.domain.TripRecapScene
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface TripRecapSceneRepository : JpaRepository<TripRecapScene, Long> {

    fun findByRecapIdAndDeletedAtIsNullOrderBySceneOrderAsc(recapId: Long): List<TripRecapScene>

    fun findByIdAndRecapIdAndDeletedAtIsNull(
        id: Long,
        recapId: Long,
    ): TripRecapScene?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update TripRecapScene scene
        set scene.deletedAt = :deletedAt,
            scene.updatedAt = :deletedAt
        where scene.recap.id = :recapId
          and scene.deletedAt is null
        """
    )
    fun softDeleteByRecapId(
        @Param("recapId") recapId: Long,
        @Param("deletedAt") deletedAt: Instant,
    ): Int
}
