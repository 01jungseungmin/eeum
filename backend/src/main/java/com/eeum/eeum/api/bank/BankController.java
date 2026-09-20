package com.eeum.eeum.api.bank;

import com.eeum.eeum.application.store.dto.response.BankResponseDto;
import com.eeum.eeum.application.store.service.BankQueryService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "36. Bank", description = "정산 계좌 은행 목록 API")
@RestController
@RequestMapping("/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankQueryService bankQueryService;

    @Operation(summary = "은행 목록 조회",
            description = "정산 계좌 등록 화면의 은행 셀렉트 박스를 채우는 목록입니다. bankName을 그대로 등록 요청에 보냅니다.")
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<ApiResponse<List<BankResponseDto>>> getBanks() {
        return ResponseEntity.ok(ApiResponse.success(bankQueryService.getBanks()));
    }
}
