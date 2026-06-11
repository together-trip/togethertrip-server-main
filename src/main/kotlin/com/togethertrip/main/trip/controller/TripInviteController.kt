package com.togethertrip.main.trip.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.controller.spec.TripInviteApiSpec
import com.togethertrip.main.trip.dto.request.JoinTripRequest
import com.togethertrip.main.trip.dto.response.JoinTripResponse
import com.togethertrip.main.trip.dto.response.TripInviteInfoResponse
import com.togethertrip.main.trip.dto.response.TripInviteResponse
import com.togethertrip.main.trip.service.TripInviteService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// invite-codes, invite-links는 /api/trips/{tripId}/ 하위,
// trip-invites, trip-invite-joins는 별도 최상위 경로이므로 클래스 레벨 @RequestMapping 없이 전체 경로를 명시합니다.
@RestController
class TripInviteController(
    private val tripInviteService: TripInviteService,
) : TripInviteApiSpec {

    @PostMapping("/api/trips/{tripId}/invite-codes")
    override fun createInviteCode(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ): ApiResponse<TripInviteResponse> {
        return ApiResponse.success(
            tripInviteService.createInviteCode(
                userId = authUser.userId,
                tripId = tripId,
            )
        )
    }

    @PostMapping("/api/trips/{tripId}/invite-links")
    override fun createInviteLink(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ): ApiResponse<TripInviteResponse> {
        return ApiResponse.success(
            tripInviteService.createInviteLink(
                userId = authUser.userId,
                tripId = tripId,
            )
        )
    }

    @GetMapping("/api/trip-invites")
    override fun getInviteInfo(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) token: String?,
    ): ApiResponse<TripInviteInfoResponse> {
        return ApiResponse.success(
            tripInviteService.getInviteInfo(
                userId = authUser.userId,
                code = code,
                token = token,
            )
        )
    }

    @PostMapping("/api/trip-invite-joins")
    override fun joinTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @Valid @RequestBody request: JoinTripRequest,
    ): ApiResponse<JoinTripResponse> {
        return ApiResponse.success(
            tripInviteService.joinTrip(
                userId = authUser.userId,
                request = request,
            )
        )
    }
}
