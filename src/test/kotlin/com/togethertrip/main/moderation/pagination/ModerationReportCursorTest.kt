package com.togethertrip.main.moderation.pagination

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModerationReportCursorTest {
    @Test
    fun `생성 시각과 id 커서를 URL safe 문자열로 왕복한다`() {
        val cursor = ModerationReportCursor(Instant.parse("2026-07-25T00:00:00Z"), 123)

        assertEquals(cursor, ModerationReportCursor.decode(cursor.encode()))
    }

    @Test
    fun `형식이 잘못된 커서를 거부한다`() {
        assertFailsWith<RuntimeException> { ModerationReportCursor.decode("invalid") }
    }
}
