package com.togethertrip.main.moderation.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.moderation.dto.request.CreateModerationReportRequest
import com.togethertrip.main.moderation.dto.response.BlockedUserResponse
import com.togethertrip.main.moderation.dto.response.BlockedUsersResponse
import com.togethertrip.main.moderation.dto.response.ModerationReportResponse
import com.togethertrip.main.moderation.service.ModerationReportService
import com.togethertrip.main.moderation.service.UserBlockService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ModerationController(
    private val moderationReportService: ModerationReportService,
    private val userBlockService: UserBlockService,
) {
    @PostMapping("/api/trips/{tripId}/reports")
    fun createReport(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @Valid @RequestBody request: CreateModerationReportRequest,
    ): ApiResponse<ModerationReportResponse> {
        return ApiResponse.success(moderationReportService.create(authUser.userId, tripId, request))
    }

    @PostMapping("/api/users/{userId}/blocks")
    fun block(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable userId: Long,
    ): ApiResponse<BlockedUserResponse> {
        return ApiResponse.success(userBlockService.block(authUser.userId, userId))
    }

    @DeleteMapping("/api/users/{userId}/blocks")
    fun unblock(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable userId: Long,
    ): ApiResponse<Unit> {
        userBlockService.unblock(authUser.userId, userId)
        return ApiResponse.success()
    }

    @GetMapping("/api/users/me/blocks")
    fun getBlocks(@AuthenticationPrincipal authUser: AuthUser): ApiResponse<BlockedUsersResponse> {
        return ApiResponse.success(userBlockService.getBlocks(authUser.userId))
    }
}
