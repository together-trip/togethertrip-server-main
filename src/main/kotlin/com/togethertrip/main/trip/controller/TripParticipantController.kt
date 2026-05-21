package com.togethertrip.main.trip.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.dto.request.AddTripParticipantRequest
import com.togethertrip.main.trip.dto.request.LinkTripParticipantRequest
import com.togethertrip.main.trip.dto.request.UpdateTripParticipantRequest
import com.togethertrip.main.trip.service.TripParticipantService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "TripParticipant", description = "여행 참여자 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/trips/{tripId}")
class TripParticipantController(
    private val tripParticipantService: TripParticipantService,
) {

    @Operation(summary = "여행 참여자 추가", description = "여행에 임시 동행자를 추가합니다.")
    @PostMapping("/participants")
    fun addParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: AddTripParticipantRequest,
    ) {
    }

    @Operation(summary = "여행 참여자 목록 조회", description = "여행의 참여자 목록을 조회합니다.")
    @GetMapping("/participants")
    fun getParticipants(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) type: String?,
    ) {
    }

    @Operation(summary = "여행 참여자 상세 조회", description = "특정 여행 참여자 정보를 조회합니다.")
    @GetMapping("/participants/{participantId}")
    fun getParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable participantId: Long,
    ) {
    }

    @Operation(summary = "여행 참여자 수정", description = "참여자의 표시명과 프로필 정보를 수정합니다.")
    @PatchMapping("/participants/{participantId}")
    fun updateParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable participantId: Long,
        @RequestBody request: UpdateTripParticipantRequest,
    ) {
    }

    @Operation(summary = "여행 참여자 제거", description = "여행 참여자를 제거합니다.")
    @DeleteMapping("/participants/{participantId}")
    fun removeParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable participantId: Long,
    ) {
    }

    @Operation(summary = "임시 참여자 연결", description = "임시 동행자를 현재 로그인 사용자와 연결합니다.")
    @PostMapping("/participant-connections")
    fun linkParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: LinkTripParticipantRequest,
    ) {
    }
}
