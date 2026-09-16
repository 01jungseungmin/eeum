package com.eeum.eeum.api.ai;

import com.eeum.eeum.application.ai.dto.request.AiMarketingDraftRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiEventPerformanceResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiMarketingDraftResponseDto;
import com.eeum.eeum.application.ai.dto.response.ChannelReachDto;
import com.eeum.eeum.application.ai.service.AiEventPerformanceService;
import com.eeum.eeum.application.ai.service.AiMarketingService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "10. Owner AI Manager", description = "사장 웹 AI 매니저 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/ai-manager")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerAiMarketingController {

    private final AiMarketingService aiMarketingService;
    private final AiEventPerformanceService aiEventPerformanceService;

    @Operation(summary = "마케팅 자동화 개요 조회", description = "채널별 예상 도달 수를 조회합니다. 데이터가 없으면 채널별 0을 반환합니다.")
    @GetMapping("/marketing")
    public ResponseEntity<ApiResponse<List<ChannelReachDto>>> getMarketingOverview() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiMarketingService.getChannelReaches(ownerId)));
    }

    @Operation(summary = "마케팅 문구 초안 생성",
            description = "공지 유형/톤/채널 기반으로 홍보 문구를 생성합니다. 채널 4종(SNS_CARD 포함) 지원. (Basic 이상, 월 사용량 카운트)"
                    + " 타입별 초안 보관 개수 캡 초과 시 409(AI_015)를 반환하며, confirmDelete=true로 재요청하면"
                    + " 가장 오래된 초안을 삭제하고 진행합니다.")
    @PostMapping("/marketing/draft")
    public ResponseEntity<ApiResponse<AiMarketingDraftResponseDto>> createMarketingDraft(
            @Valid @RequestBody AiMarketingDraftRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(aiMarketingService.createMarketingDraft(ownerId, request)));
    }

    @Operation(summary = "공지 등록용 초안 생성",
            description = "공지 발송 채널은 KAKAO_ALERT / APP_PUSH / STORE_NOTICE 3종만 허용됩니다. SNS_CARD 포함 시 AI_INVALID_CHANNEL 예외."
                    + " 타입별 초안 보관 개수 캡 초과 시 409(AI_015)를 반환하며, confirmDelete=true로 재요청하면"
                    + " 가장 오래된 초안을 삭제하고 진행합니다.")
    @PostMapping("/notices/draft")
    public ResponseEntity<ApiResponse<AiMarketingDraftResponseDto>> createNoticeDraft(
            @Valid @RequestBody AiMarketingDraftRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(aiMarketingService.createNoticeDraft(ownerId, request)));
    }

    @Operation(summary = "이벤트 성과 조회",
            description = "최근 이벤트의 주문/신규 고객/단골 재주문 성과와 다음 이벤트 추천(프리필 데이터 포함)을 조회합니다.")
    @GetMapping("/events/performance")
    public ResponseEntity<ApiResponse<AiEventPerformanceResponseDto>> getEventPerformance() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiEventPerformanceService.getPerformance(ownerId)));
    }
}
