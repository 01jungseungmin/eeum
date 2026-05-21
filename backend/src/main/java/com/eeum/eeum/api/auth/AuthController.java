package com.eeum.eeum.api.auth;

import com.eeum.eeum.application.auth.dto.request.*;
import com.eeum.eeum.application.auth.dto.response.OAuthLoginResponseDto;
import com.eeum.eeum.application.auth.dto.response.ReAuthResponseDto;
import com.eeum.eeum.application.auth.dto.response.TokenResponseDto;
import com.eeum.eeum.application.auth.service.AuthService;
import com.eeum.eeum.application.auth.service.BusinessVerificationService;
import com.eeum.eeum.common.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 API (회원가입, 로그인, 토큰 관리)")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final BusinessVerificationService businessVerificationService;

    @Operation(summary = "이메일 인증 코드 발송", description = "회원가입 전 이메일 인증 코드를 발송합니다.")
    @PostMapping("/email/send-verification")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerificationCode(
            @Valid @RequestBody EmailSendRequestDto request
    ) {
        authService.sendEmailVerificationCode(request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "이메일 인증 코드 확인", description = "발송된 인증 코드를 검증하고 회원가입용 이메일 인증 토큰을 반환합니다.")
    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<String>> verifyEmailCode(
            @Valid @RequestBody EmailVerifyRequestDto request
    ) {
        String token = authService.verifyEmailCode(request);
        return ResponseEntity.ok(ApiResponse.success(token));
    }

    @Operation(summary = "일반 회원가입", description = "이메일 인증 완료 후 일반 회원으로 가입합니다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(
            @Valid @RequestBody SignupRequestDto request
    ) {
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success());
    }

    @Operation(summary = "사장 회원가입", description = "이메일 인증 및 사업자번호 검증 후 사장 회원으로 가입 신청합니다.")
    @PostMapping("/signup/owner")
    public ResponseEntity<ApiResponse<Void>> ownerSignup(
            @Valid @RequestBody OwnerSignupRequestDto request
    ) {
        authService.ownerSignup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success());
    }

    @Operation(
            summary = "사업자등록정보 진위확인",
            description = "사장 회원가입 전 사업자등록번호, 대표자명, 개업일자를 검증합니다."
    )
    @PostMapping("/business/verify")
    public ResponseEntity<ApiResponse<Boolean>> verifyBusiness(
            @Valid @RequestBody BusinessVerifyRequestDto request
    ) {
        boolean verified = businessVerificationService.verifyBusiness(request);

        return ResponseEntity.ok(ApiResponse.success(verified));
    }

    @Operation(summary = "로컬 로그인", description = "이메일/비밀번호로 로그인합니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponseDto>> login(
            @Valid @RequestBody LoginRequestDto request
    ) {
        TokenResponseDto token = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(token));
    }

    @Operation(summary = "OAuth 로그인", description = "카카오/네이버 인가 코드로 소셜 로그인합니다.")
    @PostMapping("/login/oauth")
    public ResponseEntity<ApiResponse<OAuthLoginResponseDto>> oauthLogin(
            @Valid @RequestBody OAuthLoginRequestDto request
    ) {
        OAuthLoginResponseDto token = authService.oauthLogin(request);

        return ResponseEntity.ok(ApiResponse.success(token));
    }

    @Operation(summary = "OAuth 회원가입 추가 정보 입력 완료", description = "OAuth 신규 회원이 이름, 전화번호, 닉네임을 입력하여 회원가입을 완료합니다.")
    @PostMapping("/signup/oauth")
    public ResponseEntity<ApiResponse<TokenResponseDto>> oauthComplete(
            @Valid @RequestBody OAuthCompleteRequestDto request
    ){
        TokenResponseDto token = authService.oauthComplete(request);
        return ResponseEntity.ok(ApiResponse.success(token));
    }

    @Operation(summary = "Access Token 재발급", description = "Refresh Token으로 Access Token을 재발급합니다.")
    @PostMapping("/token/reissue")
    public ResponseEntity<ApiResponse<TokenResponseDto>> reissue(
            @Valid @RequestBody ReissueRequestDto request
    ) {
        TokenResponseDto token = authService.reissue(request);
        return ResponseEntity.ok(ApiResponse.success(token));
    }

    @Operation(summary = "로그아웃", description = "Refresh Token을 삭제하고 Access Token을 블랙리스트에 등록합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody ReissueRequestDto request
    ) {
        authService.logout(request, authorizationHeader);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "비밀번호 재설정 메일 발송", description = "비밀번호 재설정 링크를 이메일로 발송합니다.")
    @PostMapping("/password/reset-request")
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetEmail(
            @Valid @RequestBody PasswordResetRequestDto request
    ) {
        authService.sendPasswordResetEmail(request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "비밀번호 재설정 인증 코드 확인", description = "발송된 인증 코드를 검증하고 비밀번호 재설정용 이메일 인증 토큰을 반환합니다.")
    @PostMapping("/password/verify")
    public ResponseEntity<ApiResponse<String>> verifyPasswordResetCode(
            @Valid @RequestBody EmailVerifyRequestDto request
    ) {
        String token = authService.verifyResetPasswordEmailCode(request);
        return ResponseEntity.ok(ApiResponse.success(token));
    }

    @Operation(summary = "비밀번호 재설정", description = "재설정 토큰으로 새 비밀번호를 설정합니다.")
    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody PasswordNewRequestDto request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "재인증", description = "비밀번호 변경, 탈퇴 등 민감 작업 전 본인 확인용 재인증 토큰을 발급합니다.")
    @PostMapping("/reauth")
    public ResponseEntity<ApiResponse<ReAuthResponseDto>> reAuth(
            @Valid @RequestBody ReAuthRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        ReAuthResponseDto response = authService.reAuth(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}