package com.eeum.eeum.api.owner;

import com.eeum.eeum.application.reservation.dto.request.VisitReservationStatusUpdateRequestDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationLeftTimeSlotResponseDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationResponseDto;
import com.eeum.eeum.application.reservation.service.VisitReservationService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "16. Owner - Visit Reservation", description = "사장 방문 예약 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/reservations/visits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerReservationController {

    private final VisitReservationService visitReservationService;

    @Operation(summary = "방문 예약 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<VisitReservationResponseDto>>> getOwnerReservations(
            @RequestParam(required = false) VisitReservationStatus status,
            Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                visitReservationService.getOwnerReservations(accountId, status, pageable)
        ));
    }

    @Operation(summary = "방문 예약 상세 조회")
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<VisitReservationResponseDto>> getOwnerReservationDetail(
            @PathVariable Long reservationId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                visitReservationService.getOwnerReservationDetail(accountId, reservationId)
        ));
    }

    @Operation(summary = "방문 예약 승인")
    @PatchMapping("/{reservationId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveReservation(
            @PathVariable Long reservationId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        visitReservationService.approveReservation(accountId, reservationId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "방문 예약 거절")
    @PatchMapping("/{reservationId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectReservation(
            @PathVariable Long reservationId,
            @Valid @RequestBody VisitReservationStatusUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        visitReservationService.rejectReservation(accountId, reservationId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "방문 예약 완료 처리")
    @PatchMapping("/{reservationId}/complete")
    public ResponseEntity<ApiResponse<Void>> completeReservation(
            @PathVariable Long reservationId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        visitReservationService.completeReservation(accountId, reservationId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "잔여 예약 가능 시간대 조회", description = "특정 날짜의 시간대별 예약 가능 테이블 수, 예약된 테이블 수, 예약 가능 여부를 조회합니다.")
    @GetMapping("/available-time-slots")
    public ResponseEntity<ApiResponse<List<VisitReservationLeftTimeSlotResponseDto>>> getOwnerReservationTimeSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                visitReservationService.getOwnerLeftTimeSlot(accountId, date)
        ));
    }
}