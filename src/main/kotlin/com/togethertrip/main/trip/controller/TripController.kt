package com.togethertrip.main.trip.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.dto.request.AddTripParticipantRequest
import com.togethertrip.main.trip.dto.request.CreateTripRequest
import com.togethertrip.main.trip.dto.request.JoinTripRequest
import com.togethertrip.main.trip.dto.request.UpdateTripCountriesRequest
import com.togethertrip.main.trip.dto.request.UpdateTripParticipantRequest
import com.togethertrip.main.trip.dto.request.UpdateTripRequest
import com.togethertrip.main.trip.service.TripService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Trip", description = "여행 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/trips")
class TripController(
    private val tripService: TripService,
) {

    @Operation(summary = "여행 생성", description = "여행명, 국가, 기간, 기본 통화, 동행자를 입력하여 여행을 생성합니다.")
    @PostMapping
    fun createTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestBody request: CreateTripRequest,
    ) {
    }

    @Operation(summary = "여행 목록 조회", description = "현재 사용자가 참여한 여행 목록을 조회합니다.")
    @GetMapping
    fun getTrips(
        @AuthenticationPrincipal authUser: AuthUser,
    ) {
    }

    @Operation(summary = "여행 상세 조회", description = "여행 기본 정보, 국가, 참여자 정보를 조회합니다.")
    @GetMapping("/{tripId}")
    fun getTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "여행 수정", description = "여행명, 국가, 기간, 기본 통화 정보를 수정합니다.")
    @PatchMapping("/{tripId}")
    fun updateTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: UpdateTripRequest,
    ) {
    }

    @Operation(summary = "여행 삭제", description = "여행을 삭제합니다.")
    @DeleteMapping("/{tripId}")
    fun deleteTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "여행 국가 목록 변경", description = "여행의 국가 목록과 표시 순서를 변경합니다.")
    @PutMapping("/{tripId}/countries")
    fun updateTripCountries(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: UpdateTripCountriesRequest,
    ) {
    }

    // ── 참여자 ──────────────────────────────────────────────────────────────

    @Operation(summary = "여행 참여자 추가", description = "여행에 임시 동행자를 추가합니다.")
    @PostMapping("/{tripId}/participants")
    fun addParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: AddTripParticipantRequest,
    ) {
    }

    @Operation(summary = "여행 참여자 목록 조회", description = "여행의 참여자 목록을 조회합니다.")
    @GetMapping("/{tripId}/participants")
    fun getParticipants(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "여행 참여자 수정", description = "참여자의 표시명과 프로필 정보를 수정합니다.")
    @PatchMapping("/{tripId}/participants/{participantId}")
    fun updateParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable participantId: Long,
        @RequestBody request: UpdateTripParticipantRequest,
    ) {
    }

    @Operation(summary = "여행 참여자 제거", description = "여행 참여자를 제거합니다.")
    @DeleteMapping("/{tripId}/participants/{participantId}")
    fun removeParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable participantId: Long,
    ) {
    }

    @Operation(summary = "내 여행 참여자 정보 조회", description = "특정 여행에서 현재 사용자의 참여자 정보를 조회합니다.")
    @GetMapping("/{tripId}/participants/me")
    fun getMyParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "임시 참여자 연결", description = "임시 동행자를 현재 로그인 사용자와 연결합니다.")
    @PostMapping("/{tripId}/participants/{participantId}/link")
    fun linkParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable participantId: Long,
    ) {
    }

    // ── 초대 ──────────────────────────────────────────────────────────────

    @Operation(summary = "초대 코드 생성", description = "여행 참여용 초대 코드를 생성합니다.")
    @PostMapping("/{tripId}/invite/code")
    fun createInviteCode(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "초대 링크 생성", description = "여행 참여용 초대 링크를 생성합니다.")
    @PostMapping("/{tripId}/invite/link")
    fun createInviteLink(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ) {
    }

    @Operation(summary = "초대 정보 조회", description = "초대 코드 또는 초대 토큰으로 여행 초대 정보를 조회합니다.")
    @GetMapping("/invite")
    fun getInviteInfo(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) token: String?,
    ) {
    }

    @Operation(summary = "초대 참여", description = "초대 코드 또는 초대 토큰으로 여행에 참여합니다.")
    @PostMapping("/invite/join")
    fun joinTrip(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestBody request: JoinTripRequest,
    ) {
    }
}
