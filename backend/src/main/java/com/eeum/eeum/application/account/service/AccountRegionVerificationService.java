package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.LocationDto;
import com.eeum.eeum.application.account.dto.response.AccountRegionResponseDto;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.infrastructure.geo.KakaoRegionCodeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 활동 지역 GPS 인증. 현재 좌표의 법정동이 등록한 동과 같으면 인증한다.
 *
 * 동 중심점과의 거리로 판정하면 넓은 동의 가장자리 주민이 자기 동네를 인증하지 못한다.
 * 카카오 호출 동안 DB 커넥션을 쥐지 않도록 이 메서드에는 트랜잭션을 걸지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountRegionVerificationService {

    private final AccountRegionService accountRegionService;
    private final KakaoRegionCodeClient kakaoRegionCodeClient;

    public AccountRegionResponseDto verifyRegion(Long accountId, Long accountRegionId, LocationDto request) {
        // 소유권 확인을 먼저 끝내 남의 지역 ID로 카카오 쿼터를 쓰지 못하게 한다.
        String regionCode = accountRegionService.getRegionCode(accountId, accountRegionId);

        String currentRegionCode = kakaoRegionCodeClient
                .findLegalDongCode(request.getLatitude(), request.getLongitude())
                .orElse(null);

        if (!regionCode.equals(currentRegionCode)) {
            log.info("활동 지역 GPS 인증 불일치: accountId={}, accountRegionId={}", accountId, accountRegionId);
            throw new BusinessException(ErrorCode.REGION_GPS_MISMATCH);
        }

        return accountRegionService.completeVerification(accountId, accountRegionId);
    }
}
