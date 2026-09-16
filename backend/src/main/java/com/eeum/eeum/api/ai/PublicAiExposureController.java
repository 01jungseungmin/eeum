package com.eeum.eeum.api.ai;

import com.eeum.eeum.application.ai.dto.response.AiExposedStoreDto;
import com.eeum.eeum.application.ai.service.AiAdExposureService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.lock.RateLimitKeys;
import com.eeum.eeum.common.service.RateLimitService;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.common.util.ViewerKeyResolver;
import com.eeum.eeum.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

// 사용자 앱/웹용 공개 API — 생활권 매칭 노출 중인 가게 조회 + 클릭 로그
@Tag(name = "12. Public - AI Exposure", description = "생활권 매칭 노출 가게 공개 API")
@RestController
@RequestMapping("/ai-exposures")
@RequiredArgsConstructor
@Validated
public class PublicAiExposureController {

    // 비인증 공개 API라 요청 수 자체에 상한이 없다 — viewerKey(로그인 시 accountId, 아니면 IP) 단위로
    // 분당 호출 수를 제한해 노출 로그/클릭 로그 테이블이 스팸성 요청으로 무한 증가하는 것을 막는다.
    private static final int VIEW_MAX_REQUESTS_PER_MINUTE = 60;
    private static final int CLICK_MAX_REQUESTS_PER_MINUTE = 30;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    private final AiAdExposureService aiAdExposureService;
    private final RateLimitService rateLimitService;
    private final ViewerKeyResolver viewerKeyResolver;

    @Operation(summary = "노출 중인 가게 목록 조회",
            description = "생활권 매칭 노출을 시작한 가게(active=true)만 반환합니다. 노출 로그가 함께 기록됩니다. "
                    + "region 파라미터로 지역 필터링이 가능합니다.")
    @GetMapping("/stores")
    public ResponseEntity<ApiResponse<List<AiExposedStoreDto>>> getExposedStores(
            @Parameter(description = "지역 키워드 (예: 마포구)") @RequestParam(required = false) String region,
            HttpServletRequest request
    ) {
        String viewerKey = viewerKeyResolver.resolve(request);
        rateLimitService.checkAndIncrement(
                RateLimitKeys.aiExposureView(viewerKey), VIEW_MAX_REQUESTS_PER_MINUTE, RATE_LIMIT_WINDOW,
                ErrorCode.AI_RATE_LIMITED);

        Long viewerAccountId = SecurityUtil.getCurrentAccountIdOrNull();
        return ResponseEntity.ok(ApiResponse.success(
                aiAdExposureService.getExposedStores(viewerAccountId, region)));
    }

    @Operation(summary = "노출 가게 클릭 로그 기록",
            description = "노출된 가게 클릭 시 호출합니다. 전환 추적의 연결 고리로 사용됩니다.")
    @PostMapping("/{exposureStatusId}/click")
    public ResponseEntity<ApiResponse<Void>> recordClick(
            @Parameter(description = "노출 상태 ID") @PathVariable Long exposureStatusId,
            @Parameter(description = "노출 응답의 requestId")
            @RequestParam(required = false) @Size(max = 64) String requestId,
            HttpServletRequest request
    ) {
        String viewerKey = viewerKeyResolver.resolve(request);
        rateLimitService.checkAndIncrement(
                RateLimitKeys.aiExposureClick(viewerKey), CLICK_MAX_REQUESTS_PER_MINUTE, RATE_LIMIT_WINDOW,
                ErrorCode.AI_RATE_LIMITED);

        Long viewerAccountId = SecurityUtil.getCurrentAccountIdOrNull();
        aiAdExposureService.recordClick(exposureStatusId, viewerAccountId, requestId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
