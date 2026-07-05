package com.eeum.eeum.api.reservation;

import com.eeum.eeum.application.reservation.dto.request.VisitReservationCreateRequestDto;
import com.eeum.eeum.application.reservation.dto.response.TimeSlotAvailabilityResponseDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationResponseDto;
import com.eeum.eeum.application.reservation.service.VisitReservationService;
import com.eeum.eeum.application.store.dto.request.StoreReservationReviewCreateRequestDto;
import com.eeum.eeum.application.store.dto.response.StoreReviewDetailResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreReviewResponseDto;
import com.eeum.eeum.application.store.service.StoreReviewService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "18. Visit Reservation", description = "매장 방문 예약 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/reservations/visits")
@RequiredArgsConstructor
public class VisitReservationController {

    private final VisitReservationService visitReservationService;
    private final StoreReviewService storeReviewService;

    @Operation(summary = "예약 가능 시간대 조회",
            description = "날짜와 인원 수 기준으로 테이블 자동 배정 가능한 슬롯 목록을 반환합니다.")
    @GetMapping("/stores/{storeId}/time-slots")
    public ResponseEntity<ApiResponse<List<TimeSlotAvailabilityResponseDto>>> getAvailableTimeSlots(
            @PathVariable Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer partySize
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                visitReservationService.getAvailableTimeSlots(storeId, date, partySize)
        ));
    }

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

    @Operation(summary = "방문 예약 리뷰 작성", description = "방문 완료(COMPLETED) 예약 기준 리뷰를 작성합니다. 1예약 1리뷰.")
    @PostMapping("/{reservationId}/review")
    public ResponseEntity<ApiResponse<StoreReviewResponseDto>> createReservationReview(
            @PathVariable Long reservationId,
            @Valid @RequestBody StoreReservationReviewCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                storeReviewService.createReservationReview(accountId, reservationId, request)
        ));
    }

    @Operation(summary = "방문 예약 리뷰 조회", description = "내가 작성한 방문 예약 리뷰를 조회합니다.")
    @GetMapping("/{reservationId}/review")
    public ResponseEntity<ApiResponse<StoreReviewDetailResponseDto>> getReservationReview(
            @PathVariable Long reservationId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                storeReviewService.getReservationReview(accountId, reservationId)
        ));
    }
}