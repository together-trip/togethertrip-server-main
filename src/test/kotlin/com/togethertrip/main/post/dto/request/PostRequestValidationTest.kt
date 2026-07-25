package com.togethertrip.main.post.dto.request

import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import kotlin.test.assertTrue

class PostRequestValidationTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `게시글 생성과 수정의 모든 텍스트 길이를 제한한다`() {
        val longTitle = "가".repeat(101)
        val longCategory = "나".repeat(31)
        val longContent = "다".repeat(5001)
        val longPlace = "라".repeat(101)
        val create = CreatePostRequest(title = longTitle, category = longCategory, content = longContent, placeName = longPlace)
        val update = UpdatePostRequest(title = longTitle, category = longCategory, content = longContent, placeName = longPlace)
        listOf("title", "category", "content", "placeName").forEach { property ->
            assertTrue(validator.validateProperty(create, property).isNotEmpty())
            assertTrue(validator.validateProperty(update, property).isNotEmpty())
        }
    }

    @Test
    fun `지출 게시글 생성과 수정도 동일한 텍스트 길이를 제한한다`() {
        val longText = "가".repeat(5001)
        val create = CreateExpensePostRequest(content = longText)
        val update = UpdateExpensePostRequest(
            content = longText,
            amount = BigDecimal.ONE,
            currency = "KRW",
            payments = emptyList(),
            shares = emptyList(),
        )
        assertTrue(validator.validateProperty(create, "content").isNotEmpty())
        assertTrue(validator.validateProperty(update, "content").isNotEmpty())
    }

    @Test
    fun `댓글은 2000자 첨부는 10개로 제한한다`() {
        assertTrue(validator.validate(CreatePostCommentRequest("가".repeat(2001))).isNotEmpty())
        val files = List(11) { mock(MultipartFile::class.java) }
        assertTrue(validator.validateProperty(CreatePostRequest(files = files), "files").isNotEmpty())
        assertTrue(validator.validateProperty(UpdatePostRequest(files = files), "files").isNotEmpty())
        assertTrue(validator.validateProperty(CreateExpensePostRequest(files = files), "files").isNotEmpty())
    }
}
