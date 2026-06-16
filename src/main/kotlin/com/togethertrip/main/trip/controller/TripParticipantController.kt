package com.togethertrip.main.trip.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.trip.controller.spec.TripParticipantApiSpec
import com.togethertrip.main.trip.dto.request.AddTripParticipantRequest
import com.togethertrip.main.trip.dto.request.LinkTripParticipantRequest
import com.togethertrip.main.trip.dto.request.UpdateTripParticipantRequest
import com.togethertrip.main.trip.dto.response.TripParticipantSummaryResponse
import com.togethertrip.main.trip.service.TripParticipantService
import jakarta.validation.Valid
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

@RestController
@RequestMapping("/api/trips/{tripId}")
class TripParticipantController(
    private val tripParticipantService: TripParticipantService,
) : TripParticipantApiSpec {

    @PostMapping("/participants")
    override fun addParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @Valid @RequestBody request: AddTripParticipantRequest,
    ): ApiResponse<TripParticipantSummaryResponse> {
        return ApiResponse.success(
            tripParticipantService.addTemporaryParticipant(
                userId = authUser.userId,
                tripId = tripId,
                request = request,
            )
        )
    }

    @GetMapping("/participants")
    override fun getParticipants(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) type: String?,
    ): ApiResponse<List<TripParticipantSummaryResponse>> {
        return ApiResponse.success(
            tripParticipantService.getParticipants(
                userId = authUser.userId,
                tripId = tripId,
                status = status,
                type = type,
            )
        )
    }

    @GetMapping("/participants/{participantId}")
    override fun getParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable participantId: Long,
    ): ApiResponse<TripParticipantSummaryResponse> {
        return ApiResponse.success(
            tripParticipantService.getParticipant(
                userId = authUser.userId,
                tripId = tripId,
                participantId = participantId,
            )
        )
    }

    @PatchMapping("/participants/{participantId}")
    override fun updateParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable participantId: Long,
        @Valid @RequestBody request: UpdateTripParticipantRequest,
    ): ApiResponse<TripParticipantSummaryResponse> {
        return ApiResponse.success(
            tripParticipantService.updateParticipant(
                userId = authUser.userId,
                tripId = tripId,
                participantId = participantId,
                request = request,
            )
        )
    }

    @DeleteMapping("/participants/{participantId}")
    override fun removeParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable participantId: Long,
    ): ApiResponse<Unit> {
        tripParticipantService.removeParticipant(
            userId = authUser.userId,
            tripId = tripId,
            participantId = participantId,
        )

        return ApiResponse.success()
    }

    @PostMapping("/participant-connections")
    override fun linkParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @Valid @RequestBody request: LinkTripParticipantRequest,
    ): ApiResponse<TripParticipantSummaryResponse> {
        return ApiResponse.success(
            tripParticipantService.linkTemporaryParticipant(
                userId = authUser.userId,
                tripId = tripId,
                request = request,
            )
        )
    }
}
