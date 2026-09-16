package com.eeum.eeum.api.ai;

import com.eeum.eeum.application.ai.dto.request.AiChatMessageRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiChatQuickQuestionDto;
import com.eeum.eeum.application.ai.dto.response.AiChatResponseDto;
import com.eeum.eeum.application.ai.service.AiChatbotService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "10. Owner AI Manager", description = "사장 웹 AI 매니저 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/ai-manager/chat")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerAiChatbotController {

    private final AiChatbotService aiChatbotService;

    @Operation(summary = "챗봇 고정 추천 질문 조회", description = "고정 추천 질문 8종을 조회합니다.")
    @GetMapping("/quick-questions")
    public ResponseEntity<ApiResponse<List<AiChatQuickQuestionDto>>> getQuickQuestions() {
        return ResponseEntity.ok(ApiResponse.success(aiChatbotService.getQuickQuestions()));
    }

    @Operation(summary = "챗봇 메시지 전송",
            description = "고정 질문 ID 또는 자유 입력 텍스트로 질문합니다. 범위 밖 키워드(세무/노무/법률 등)는 안내 메시지를 반환하며, "
                    + "문구 생성성 답변은 월 사용량에 카운트됩니다. (Basic 이상)")
    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<AiChatResponseDto>> sendMessage(
            @Valid @RequestBody AiChatMessageRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiChatbotService.answer(ownerId, request)));
    }
}
