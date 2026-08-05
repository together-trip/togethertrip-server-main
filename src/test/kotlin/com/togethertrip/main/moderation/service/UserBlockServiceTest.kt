package com.togethertrip.main.moderation.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.moderation.domain.UserBlock
import com.togethertrip.main.moderation.exception.ModerationErrorCode
import com.togethertrip.main.moderation.repository.UserBlockRepository
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UserBlockServiceTest {
    private lateinit var users: UserRepository
    private lateinit var participants: TripParticipantRepository
    private lateinit var blocks: UserBlockRepository
    private lateinit var service: UserBlockService

    @BeforeEach
    fun setUp() {
        users = mock(UserRepository::class.java)
        participants = mock(TripParticipantRepository::class.java)
        blocks = mock(UserBlockRepository::class.java)
        service = UserBlockService(users, participants, blocks)
    }

    @Test
    fun `자기 자신은 차단할 수 없다`() {
        assertEquals(ModerationErrorCode.SELF_BLOCK_NOT_ALLOWED,
            assertBusinessException { service.block(1, 1) }.errorCode)
    }

    @Test
    fun `공유 여행이 없는 사용자 ID 추측 차단을 거부한다`() {
        mockUsers()
        `when`(participants.existsSharedActiveTrip(1, 2)).thenReturn(false)
        assertEquals(ModerationErrorCode.TARGET_NOT_FOUND,
            assertBusinessException { service.block(1, 2) }.errorCode)
    }

    @Test
    fun `동일 차단 요청은 기존 결과를 반환한다`() {
        val (blocker, blocked) = mockUsers()
        `when`(participants.existsSharedActiveTrip(1, 2)).thenReturn(true)
        val existing = UserBlock(blocker, blocked).apply { id = 3 }
        `when`(blocks.findByBlockerIdAndBlockedIdAndDeletedAtIsNull(1, 2)).thenReturn(existing)
        assertEquals(2, service.block(1, 2).blockedUserId)
    }

    @Test
    fun `신규 차단은 upsert 후 저장 결과를 반환한다`() {
        val (blocker, blocked) = mockUsers()
        `when`(participants.existsSharedActiveTrip(1, 2)).thenReturn(true)
        val saved = UserBlock(blocker, blocked).apply { id = 3 }
        `when`(blocks.findByBlockerIdAndBlockedIdAndDeletedAtIsNull(1, 2))
            .thenReturn(null, saved)
        `when`(blocks.insertIfAbsent(1, 2)).thenReturn(1)
        assertEquals(2, service.block(1, 2).blockedUserId)
        verify(blocks).insertIfAbsent(1, 2)
    }

    @Test
    fun `upsert 후에도 차단을 찾지 못하면 명시적으로 실패한다`() {
        mockUsers()
        `when`(participants.existsSharedActiveTrip(1, 2)).thenReturn(true)
        `when`(blocks.findByBlockerIdAndBlockedIdAndDeletedAtIsNull(1, 2)).thenReturn(null)
        assertEquals(ModerationErrorCode.BLOCK_NOT_FOUND,
            assertBusinessException { service.block(1, 2) }.errorCode)
    }

    @Test
    fun `없는 차단은 해제할 수 없다`() {
        assertEquals(ModerationErrorCode.BLOCK_NOT_FOUND,
            assertBusinessException { service.unblock(1, 2) }.errorCode)
    }

    @Test
    fun `없는 사용자의 차단 목록은 조회할 수 없다`() {
        assertEquals(ModerationErrorCode.TARGET_NOT_FOUND,
            assertBusinessException { service.getBlocks(404) }.errorCode)
    }

    @Test
    fun `차단 해제는 이력을 soft delete로 보존한다`() {
        val (blocker, blocked) = mockUsers()
        val existing = UserBlock(blocker, blocked)
        `when`(blocks.findByBlockerIdAndBlockedIdAndDeletedAtIsNull(1, 2)).thenReturn(existing)
        service.unblock(1, 2)
        assertNotNull(existing.deletedAt)
    }

    @Test
    fun `차단 목록은 최신순 저장 결과를 반환한다`() {
        val (blocker, blocked) = mockUsers()
        val existing = UserBlock(blocker, blocked)
        `when`(blocks.findAllByBlockerIdAndDeletedAtIsNullOrderByCreatedAtDesc(1)).thenReturn(listOf(existing))
        assertEquals(listOf(2L), service.getBlocks(1).items.map { it.blockedUserId })
        verify(users).findByIdAndDeletedAtIsNull(1)
    }

    private fun mockUsers(): Pair<User, User> {
        val blocker = User("신고자").apply { id = 1 }
        val blocked = User("대상").apply { id = 2 }
        `when`(users.findByIdAndDeletedAtIsNull(1)).thenReturn(blocker)
        `when`(users.findByIdAndDeletedAtIsNull(2)).thenReturn(blocked)
        return blocker to blocked
    }

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try { block(); error("BusinessException expected") } catch (exception: BusinessException) { exception }
    }
}
