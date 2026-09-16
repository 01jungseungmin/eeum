package com.eeum.eeum.api.ai;

import com.eeum.eeum.application.ai.dto.request.AiCustomerCareDraftRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiCustomerCareCardDto;
import com.eeum.eeum.application.ai.dto.response.AiGeneratedMessageResponseDto;
import com.eeum.eeum.application.ai.service.AiCustomerCareService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.domain.ai.enums.AiCareType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "10. Owner AI Manager", description = "사장 웹 AI 매니저 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/ai-manager/customer-care")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerAiCustomerCareController {

    private final AiCustomerCareService aiCustomerCareService;

    @Operation(summary = "AI 고객 케어 카드 전체 조회",
            description = "구매 관심 / 방문 없는 단골 / 문의 후 망설임 3개 카드를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AiCustomerCareCardDto>>> getCareCards() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiCustomerCareService.getCareCards(ownerId)));
    }

    @Operation(summary = "AI 고객 케어 카드 단건 조회")
    @GetMapping("/{careType}")
    public ResponseEntity<ApiResponse<AiCustomerCareCardDto>> getCareCard(
            @Parameter(description = "케어 유형") @PathVariable AiCareType careType
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiCustomerCareService.getCareCard(ownerId, careType)));
    }

    @Operation(summary = "AI 고객 케어 메시지 초안 생성",
            description = "케어 유형별 메시지 초안을 생성해 AiGeneratedMessage로 저장합니다. (Basic 이상, 월 사용량 카운트)"
                    + " 타입별 초안 보관 개수 캡 초과 시 409(AI_015)를 반환하며, confirmDelete=true로 재요청하면"
                    + " 가장 오래된 초안을 삭제하고 진행합니다.")
    @PostMapping("/{careType}/draft")
    public ResponseEntity<ApiResponse<AiGeneratedMessageResponseDto>> createDraft(
            @Parameter(description = "케어 유형") @PathVariable AiCareType careType,
            @RequestBody(required = false) @Valid AiCustomerCareDraftRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(aiCustomerCareService.createDraft(ownerId, careType, request)));
    }
}
