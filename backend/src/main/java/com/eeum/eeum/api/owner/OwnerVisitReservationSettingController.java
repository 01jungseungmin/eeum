package com.eeum.eeum.api.owner;

import com.eeum.eeum.application.reservation.dto.request.StoreTableConfigRequestDto;
import com.eeum.eeum.application.reservation.dto.request.VisitReservationSettingUpdateRequestDto;
import com.eeum.eeum.application.reservation.dto.request.VisitReservationTimeSlotUpdateRequestDto;
import com.eeum.eeum.application.reservation.dto.response.*;
import com.eeum.eeum.application.reservation.service.StoreTableService;
import com.eeum.eeum.application.reservation.service.VisitReservationSettingService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "17. Owner - Visit Reservation Setting", description = "사장 방문 예약 설정 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/reservations/visits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerVisitReservationSettingController {

    private final VisitReservationSettingService visitReservationSettingService;
    private final StoreTableService storeTableService;

    @Operation(summary = "방문 예약 기본 설정 조회")
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<VisitReservationSettingResponseDto>> getSetting() {
        Long accountId = SecurityUtil.getCurrentAccountId();

        return ResponseEntity.ok(ApiResponse.success(
                visitReservationSettingService.getSetting(accountId)
        ));
    }

    @Operation(summary = "방문 예약 기본 설정 수정")
    @PatchMapping("/settings")
    public ResponseEntity<ApiResponse<VisitReservationSettingResponseDto>> updateSetting(
            @Valid @RequestBody VisitReservationSettingUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();

        return ResponseEntity.ok(ApiResponse.success(
                visitReservationSettingService.updateSetting(accountId, request)
        ));
    }

    @Operation(summary = "특정 날짜 방문 예약 시간대 설정 조회")
    @GetMapping("/time-slots")
    public ResponseEntity<ApiResponse<List<VisitReservationTimeSlotResponseDto>>> getTimeSlots(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();

        return ResponseEntity.ok(ApiResponse.success(
                visitReservationSettingService.getTimeSlots(accountId, date)
        ));
    }

    @Operation(summary = "특정 날짜 방문 예약 시간대 설정 저장")
    @PutMapping("/time-slots")
    public ResponseEntity<ApiResponse<List<VisitReservationTimeSlotResponseDto>>> updateTimeSlots(
            @Valid @RequestBody VisitReservationTimeSlotUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();

        return ResponseEntity.ok(ApiResponse.success(
                visitReservationSettingService.updateTimeSlots(accountId, request)
        ));
    }

    @Operation(summary = "테이블 구성 조회")
    @GetMapping("/tables")
    public ResponseEntity<ApiResponse<StoreTableListResponseDto>> getTables() {
        Long accountId = SecurityUtil.getCurrentAccountId();

        return ResponseEntity.ok(
                ApiResponse.success(storeTableService.getTables(accountId))
        );
    }

    @Operation(summary = "테이블 구성 요약 조회", description = "현재 상점의 활성 테이블 기준 수용 인원별 테이블 개수와 총 개수를 반환합니다.")
    @GetMapping("/tables/summary")
    public ResponseEntity<ApiResponse<StoreTableSummaryResponseDto>> getTableSummary() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(storeTableService.getTableSummary(accountId)));
    }

    @Operation(summary = "테이블 구성 저장", description = "기존 테이블 비활성화 후 새 테이블 생성. capacity 기준 가장 작은 테이블 자동 배정.")
    @PutMapping("/tables")
    public ResponseEntity<ApiResponse<List<StoreTableResponseDto>>> configureTables(
            @Valid @RequestBody StoreTableConfigRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(storeTableService.configureTables(accountId, request)));
    }
}