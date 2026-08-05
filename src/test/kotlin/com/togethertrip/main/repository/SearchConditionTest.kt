package com.togethertrip.main.repository

import com.togethertrip.main.post.repository.PostCommentSearchCondition
import com.togethertrip.main.post.repository.PostSearchCondition
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.repository.TransactionSearchCondition
import com.togethertrip.main.trip.repository.TripSearchCondition
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFailsWith

class SearchConditionTest {
    private val cursorCreatedAt = Instant.parse("2026-07-25T00:00:00Z")

    @Test
    fun `여행 커서 시각과 ID는 함께 입력해야 한다`() {
        assertFailsWith<IllegalArgumentException> { TripSearchCondition(1L, null, cursorCreatedAt, null) }
        assertFailsWith<IllegalArgumentException> { TripSearchCondition(1L, null, null, 1L) }
    }

    @Test
    fun `거래 커서 시각과 ID는 함께 입력해야 한다`() {
        assertFailsWith<IllegalArgumentException> {
            TransactionSearchCondition(1L, TransactionStatus.ACTIVE, null, null, cursorCreatedAt, null)
        }
        assertFailsWith<IllegalArgumentException> {
            TransactionSearchCondition(1L, TransactionStatus.ACTIVE, null, null, null, 1L)
        }
    }

    @Test
    fun `게시글 커서 시각과 ID는 함께 입력해야 한다`() {
        assertFailsWith<IllegalArgumentException> { PostSearchCondition(1L, null, null, cursorCreatedAt, null) }
        assertFailsWith<IllegalArgumentException> { PostSearchCondition(1L, null, null, null, 1L) }
    }

    @Test
    fun `댓글 커서 시각과 ID는 함께 입력해야 한다`() {
        assertFailsWith<IllegalArgumentException> { PostCommentSearchCondition(1L, null, cursorCreatedAt, null) }
        assertFailsWith<IllegalArgumentException> { PostCommentSearchCondition(1L, null, null, 1L) }
    }
}
