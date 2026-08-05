package com.togethertrip.main.moderation.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.moderation.exception.ModerationErrorCode
import org.springframework.stereotype.Component

fun interface ContentModerationFilter {
    fun validate(vararg values: String?)
}

@Component
class DefaultContentModerationFilter : ContentModerationFilter {
    override fun validate(vararg values: String?) {
        val normalized = values.filterNotNull().joinToString(" ").lowercase()
        if (BLOCKED_PATTERNS.any(normalized::contains)) {
            throw BusinessException(ModerationErrorCode.CONTENT_REJECTED)
        }
    }

    private companion object {
        val BLOCKED_PATTERNS = listOf(
            "죽여버린다",
            "신상 턴다",
            "불법촬영",
        )
    }
}
