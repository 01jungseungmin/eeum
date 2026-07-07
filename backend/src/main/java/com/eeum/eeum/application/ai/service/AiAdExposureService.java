package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiExposedStoreDto;
import com.eeum.eeum.domain.ai.entity.AiAdClickLog;
import com.eeum.eeum.domain.ai.entity.AiAdExposureLog;
import com.eeum.eeum.domain.ai.entity.AiExposureStatus;
import com.eeum.eeum.domain.ai.repository.AiAdClickLogRepository;
import com.eeum.eeum.domain.ai.repository.AiAdExposureLogRepository;
import com.eeum.eeum.domain.ai.repository.AiExposureStatusRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 생활권 광고 노출 집행 — active=true인 가게만 사용자 앱/웹에 노출한다.
 * 노출/클릭 로그를 저장해 전환 추적과 연결할 수 있게 한다. (과금/리포트 고도화는 3차)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAdExposureService {

    private final AiExposureStatusRepository aiExposureStatusRepository;
    private final AiAdExposureLogRepository aiAdExposureLogRepository;
    private final AiAdClickLogRepository aiAdClickLogRepository;

    // 노출 중인 가게 목록 — 노출 대상 0개여도 정상(빈 리스트) 응답
    @Transactional
    public List<AiExposedStoreDto> getExposedStores(Long viewerAccountId, String regionKeyword) {
        List<AiExposureStatus> activeExposures = aiExposureStatusRepository.findByActiveTrue();
        String requestId = UUID.randomUUID().toString();

        List<AiExposedStoreDto> exposedStores = activeExposures.stream()
                .filter(exposure -> matchesRegion(exposure, regionKeyword))
                .map(exposure -> {
                    // 노출 로그 — viewer는 로그인 사용자만 기록 (개인정보 최소화)
                    aiAdExposureLogRepository.save(AiAdExposureLog.record(
                            exposure.getStore(), exposure.getAiExposureStatusId(),
                            viewerAccountId, "LOCAL_MATCH", requestId));
                    return AiExposedStoreDto.from(exposure, requestId);
                })
                .toList();
        return exposedStores;
    }

    @Transactional
    public void recordClick(Long exposureStatusId, Long viewerAccountId, String requestId) {
        AiExposureStatus exposure = aiExposureStatusRepository.findById(exposureStatusId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND));
        aiAdClickLogRepository.save(AiAdClickLog.record(
                exposure.getStore(), exposureStatusId, viewerAccountId, requestId));
    }

    // 반경/관심사 조건 반영 — 지역 키워드가 있으면 가게 주소와 매칭 (정교한 GPS 반경 계산은 3차)
    private boolean matchesRegion(AiExposureStatus exposure, String regionKeyword) {
        if (regionKeyword == null || regionKeyword.isBlank()) {
            return true;
        }
        String address = exposure.getStore().getAddress();
        return address != null && address.contains(regionKeyword);
    }
}
