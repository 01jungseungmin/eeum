package com.eeum.eeum.api.reservation;

import com.eeum.eeum.application.reservation.dto.request.VisitReservationCreateRequestDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationResponseDto;
import com.eeum.eeum.application.reservation.service.VisitReservationService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "18. Visit Reservation", description = "매장 방문 예약 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/reservations/visits")
@RequiredArgsConstructor
public class VisitReservationController {

    private final VisitReservationService visitReservationService;

    @Operation(summary = "매장 방문 예약 생성")
    @PostMapping("/stores/{storeId}")
    public ResponseEntity<ApiResponse<VisitReservationResponseDto>> createReservation(
            @PathVariable Long storeId,
            @Valid @RequestBody VisitReservationCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                visitReservationService.createReservation(accountId, storeId, request)
        ));
    }

    @Operation(summary = "내 방문 예약 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<VisitReservationResponseDto>>> getMyReservations(
            Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                visitReservationService.getMyReservations(accountId, pageable)
        ));
    }

    @Operation(summary = "내 방문 예약 상세 조회")
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<VisitReservationResponseDto>> getMyReservationDetail(
            @PathVariable Long reservationId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                visitReservationService.getMyReservationDetail(accountId, reservationId)
        ));
    }

    @Operation(summary = "내 방문 예약 취소")
    @PatchMapping("/{reservationId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelMyReservation(
            @PathVariable Long reservationId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        visitReservationService.cancelMyReservation(accountId, reservationId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}