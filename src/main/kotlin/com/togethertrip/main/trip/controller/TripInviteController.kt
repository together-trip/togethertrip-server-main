package com.togethertrip.main.trip.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.dto.request.JoinTripRequest
import com.togethertrip.main.trip.service.TripInviteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// invite-codes, invite-links는 /api/trips/{tripId}/ 하위,
// trip-invites, trip-invite-joins는 별도 최상위 경로이므로 클래스 레벨 @RequestMapping 없이 전체 경로를 명시합니다.
@Tag(name = "TripInvite", description = "여행 초대 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
class TripInviteController(
    private val tripInviteService: TripInviteService,
) {

    @Operation(summary = "초대 코드 생성", description = "여행 참여용 초대 코드를 생성합니다.")
    @PostMapping("/api/trips/{tripId}/invite-codes")
    fun createInviteCode(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "초대 링크 생성", description = "여행 참여용 초대 링크를 생성합니다.")
    @PostMapping("/api/trips/{tripId}/invite-links")
    fun createInviteLink(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "초대 정보 조회", description = "초대 코드 또는 초대 토큰으로 여행 초대 정보를 조회합니다.")
    @GetMapping("/api/trip-invites")
    fun getInviteInfo(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) token: String?,
    ) {
    }

    @Operation(summary = "초대 참여", description = "초대 코드 또는 초대 토큰으로 여행에 참여합니다.")
    @PostMapping("/api/trip-invite-joins")
    fun joinTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestBody request: JoinTripRequest,
    ) {
    }
}
