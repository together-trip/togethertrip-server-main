package com.togethertrip.main.moderation.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.moderation.domain.ModerationAction
import com.togethertrip.main.moderation.domain.ModerationReportReason
import com.togethertrip.main.moderation.domain.ModerationReportStatus
import com.togethertrip.main.moderation.domain.ModerationTargetType
import com.togethertrip.main.moderation.dto.request.CreateModerationReportRequest
import com.togethertrip.main.moderation.dto.request.HandleModerationReportRequest
import com.togethertrip.main.moderation.dto.response.BlockedUserResponse
import com.togethertrip.main.moderation.dto.response.BlockedUsersResponse
import com.togethertrip.main.moderation.dto.response.ModerationReportResponse
import com.togethertrip.main.moderation.service.AdminModerationService
import com.togethertrip.main.moderation.service.ModerationReportService
import com.togethertrip.main.moderation.service.UserBlockService
import com.togethertrip.main.user.domain.UserRole
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.Page
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModerationControllerTest {
    private val reports = mock(ModerationReportService::class.java)
    private val blocks = mock(UserBlockService::class.java)
    private val controller = ModerationController(reports, blocks)
    private val user = AuthUser(1, UserRole.USER)

    @Test
    fun `신고 생성 요청을 인증 사용자와 여행으로 전달한다`() {
        val request = CreateModerationReportRequest(
            ModerationTargetType.POST, 20, ModerationReportReason.SPAM, null
        )
        `when`(reports.create(1, 10, request)).thenReturn(reportResponse())
        assertEquals(30, controller.createReport(user, 10, request).data?.id)
    }

    @Test
    fun `차단 생성 해제 목록 API를 서비스에 위임한다`() {
        val response = BlockedUserResponse(2, "대상", Instant.EPOCH)
        `when`(blocks.block(1, 2)).thenReturn(response)
        `when`(blocks.getBlocks(1)).thenReturn(BlockedUsersResponse(listOf(response)))
        assertEquals(2, controller.block(user, 2).data?.blockedUserId)
        assertTrue(controller.unblock(user, 2).success)
        assertEquals(1, controller.getBlocks(user).data?.items?.size)
        verify(blocks).unblock(1, 2)
    }

    @Test
    fun `관리자 조회와 처리 API를 인증 사용자로 위임한다`() {
        val adminService = mock(AdminModerationService::class.java)
        val adminController = AdminModerationController(adminService)
        val admin = AuthUser(9, UserRole.ADMIN)
        val request = HandleModerationReportRequest(
            ModerationReportStatus.RESOLVED, ModerationAction.HIDE, "확인", null
        )
        `when`(adminService.getReports(9, ModerationReportStatus.PENDING, null, 0, 20))
            .thenReturn(Page.empty())
        `when`(adminService.handle(9, 30, request)).thenReturn(reportResponse())
        assertTrue(adminController.getReports(admin, ModerationReportStatus.PENDING, null, 0, 20).data?.isEmpty == true)
        assertEquals(30, adminController.handle(admin, 30, request).data?.id)
    }

    private fun reportResponse() = ModerationReportResponse(
        id = 30, tripId = 10, reporterUserId = 1, targetType = ModerationTargetType.POST,
        targetId = 20, targetUserId = 2, reason = ModerationReportReason.SPAM,
        description = null, status = ModerationReportStatus.PENDING,
        handledByUserId = null, handledAt = null, createdAt = Instant.EPOCH,
    )
}
