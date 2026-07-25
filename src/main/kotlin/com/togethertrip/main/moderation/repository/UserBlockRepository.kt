package com.togethertrip.main.moderation.repository

import com.togethertrip.main.moderation.domain.UserBlock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Modifying

interface UserBlockRepository : JpaRepository<UserBlock, Long> {
    @Modifying
    @Query(
        value = """
        INSERT INTO user_blocks(blocker_user_id, blocked_user_id)
        VALUES (:blockerUserId, :blockedUserId)
        ON CONFLICT (blocker_user_id, blocked_user_id) WHERE deleted_at IS NULL
        DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(blockerUserId: Long, blockedUserId: Long): Int

    fun findByBlockerIdAndBlockedIdAndDeletedAtIsNull(blockerId: Long, blockedId: Long): UserBlock?
    fun findAllByBlockerIdAndDeletedAtIsNullOrderByCreatedAtDesc(blockerId: Long): List<UserBlock>

    @Query(
        """
        select case when count(b) > 0 then true else false end
        from UserBlock b
        where b.deletedAt is null
          and ((b.blocker.id = :firstUserId and b.blocked.id = :secondUserId)
            or (b.blocker.id = :secondUserId and b.blocked.id = :firstUserId))
        """
    )
    fun existsBidirectionalBlock(firstUserId: Long, secondUserId: Long): Boolean

    @Query(
        """
        select case when count(b) > 0 then true else false end
        from UserBlock b
        where b.deletedAt is null
          and b.blocker.id = :viewerUserId
          and b.blocked.id = :authorUserId
        """
    )
    fun existsViewerBlock(viewerUserId: Long, authorUserId: Long): Boolean

    @Query(
        """
        select u.id from User u
        where u.id in :recipientUserIds
          and u.deletedAt is null
          and not exists (
            select b.id from UserBlock b
            where b.deletedAt is null
              and ((b.blocker.id = :actorUserId and b.blocked.id = u.id)
                or (b.blocker.id = u.id and b.blocked.id = :actorUserId))
          )
        """
    )
    fun findNotBlockedRecipientUserIds(actorUserId: Long, recipientUserIds: Collection<Long>): List<Long>
}
