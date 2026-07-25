package com.togethertrip.main.moderation.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.moderation.domain.ModerationReportStatus
import com.togethertrip.main.moderation.domain.ModerationTargetType
import com.togethertrip.main.moderation.dto.request.HandleModerationReportRequest
import com.togethertrip.main.moderation.dto.response.ModerationReportResponse
import com.togethertrip.main.moderation.service.AdminModerationService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/moderation/reports")
class AdminModerationController(private val adminModerationService: AdminModerationService) {
    @GetMapping
    fun getReports(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestParam(required = false) status: ModerationReportStatus?,
        @RequestParam(required = false) targetType: ModerationTargetType?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<Page<ModerationReportResponse>> {
        return ApiResponse.success(adminModerationService.getReports(authUser.userId, status, targetType, page, size))
    }

    @PatchMapping("/{reportId}")
    fun handle(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable reportId: Long,
        @Valid @RequestBody request: HandleModerationReportRequest,
    ): ApiResponse<ModerationReportResponse> {
        return ApiResponse.success(adminModerationService.handle(authUser.userId, reportId, request))
    }
}
