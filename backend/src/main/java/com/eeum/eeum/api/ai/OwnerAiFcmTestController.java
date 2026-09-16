package com.eeum.eeum.api.ai;

import com.eeum.eeum.application.ai.dto.request.AiFcmTestSendRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiFcmTestSendResponseDto;
import com.eeum.eeum.application.ai.service.AiFcmTestService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("!prod")
@Tag(name = "10. Owner AI Manager", description = "사장 웹 AI 매니저 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/ai-manager/test")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerAiFcmTestController {

    private final AiFcmTestService aiFcmTestService;

    @Operation(summary = "FCM 단건 테스트 발송",
            description = "발표/검증용 단건 푸시 발송입니다. messageId가 있으면 해당 AI 생성 메시지의 제목/본문을, "
                    + "없으면 요청의 title/content를 사용합니다. fcmToken은 직접 입력합니다. "
                    + "(prod 프로필에서만 실제 FCM 발송, 그 외 프로필은 NoOp 로그 처리)")
    @PostMapping("/fcm")
    public ResponseEntity<ApiResponse<AiFcmTestSendResponseDto>> sendTestPush(
            @RequestBody @Validated AiFcmTestSendRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiFcmTestService.sendTestPush(ownerId, request)));
    }
}
