package com.eeum.eeum.infrastructure.geo;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 좌표가 속한 법정동 코드를 카카오 coord2regioncode로 조회한다.
 *
 * 호출 실패와 "법정동이 없는 좌표"를 구분한다. 실패를 빈 값으로 삼키면 카카오 장애가
 * 사용자에게 "위치가 동네와 다르다"는 거짓 안내로 바뀐다.
 */
@Slf4j
@Component
public class KakaoRegionCodeClient {

    private static final String PATH = "/v2/local/geo/coord2regioncode.json";
    private static final String LEGAL_DONG_TYPE = "B";
    private static final Pattern LEGAL_DONG_CODE = Pattern.compile("\\d{10}");

    // 사용자가 화면에서 기다리는 요청이다 — 공용 RestClient(read 10초)보다 짧게 끊는다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final KakaoLocalProperties properties;
    private final RestClient restClient;

    public KakaoRegionCodeClient(KakaoLocalProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.localBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * @return 읍면동 단위로 맞춘 법정동 코드. 바다·국외처럼 법정동이 없는 좌표면 빈 값
     * @throws BusinessException REGION_GEOCODE_UNAVAILABLE — 키 미설정, 통신 실패, 응답 계약 위반
     */
    public Optional<String> findLegalDongCode(double latitude, double longitude) {
        if (!properties.hasRestApiKey()) {
            log.error("[KakaoLocal] REST API 키 미설정 — 동네 인증을 처리할 수 없다");
            throw new BusinessException(ErrorCode.REGION_GEOCODE_UNAVAILABLE);
        }

        KakaoRegionCodeResponse response = request(latitude, longitude);
        if (response == null || response.documents() == null) {
            log.error("[KakaoLocal] coord2regioncode 응답에 documents가 없다 — 계약 변경 여부 확인 필요");
            throw new BusinessException(ErrorCode.REGION_GEOCODE_UNAVAILABLE);
        }

        Optional<String> legalDongCode = response.documents().stream()
                .filter(document -> LEGAL_DONG_TYPE.equals(document.regionType()))
                .map(KakaoRegionCodeResponse.Document::code)
                .findFirst();
        legalDongCode.ifPresent(KakaoRegionCodeClient::requireLegalDongCodeFormat);
        return legalDongCode.map(KakaoRegionCodeClient::toEupMyeonDongCode);
    }

    private KakaoRegionCodeResponse request(double latitude, double longitude) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(PATH)
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                    .retrieve()
                    .body(KakaoRegionCodeResponse.class);
        } catch (RestClientResponseException e) {
            // 401·403·429는 키·앱 설정·쿼터 문제라 사람이 손봐야 풀린다.
            log.error("[KakaoLocal] coord2regioncode 응답 오류: status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.REGION_GEOCODE_UNAVAILABLE);
        } catch (RuntimeException e) {
            // 예외 메시지에는 요청 URL, 즉 사용자의 정확한 좌표가 실린다 — 근본 원인만 남긴다.
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
            log.warn("[KakaoLocal] coord2regioncode 통신 실패: {}: {}",
                    cause.getClass().getSimpleName(), cause == e ? "" : cause.getMessage());
            throw new BusinessException(ErrorCode.REGION_GEOCODE_UNAVAILABLE);
        }
    }

    private static void requireLegalDongCodeFormat(String code) {
        if (code == null || !LEGAL_DONG_CODE.matcher(code).matches()) {
            log.error("[KakaoLocal] 법정동 코드 형식이 10자리 숫자가 아니다: code={}", code);
            throw new BusinessException(ErrorCode.REGION_GEOCODE_UNAVAILABLE);
        }
    }

    // 읍·면 지역은 카카오가 리 단위 코드를 준다. region 테이블은 읍면동 단위라 끝 두 자리(리)를 비운다.
    private static String toEupMyeonDongCode(String legalDongCode) {
        return legalDongCode.substring(0, 8) + "00";
    }
}
