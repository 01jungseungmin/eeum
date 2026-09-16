package com.eeum.eeum.application.region.service;

import com.eeum.eeum.application.region.dto.response.Coordinate;
import com.eeum.eeum.application.region.dto.response.KakaoAddressResponseDto;
import com.eeum.eeum.config.RestClientConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final RestClientConfig restClientConfig;

    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    public Coordinate getCoordinate(String address) {
        try {
            String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);

            String url = "https://dapi.kakao.com/v2/local/search/address.json"
                    + "?query=" + encodedAddress;

            KakaoAddressResponseDto response = restClientConfig.restClient()
                    .get()
                    .uri(URI.create(url))
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .body(KakaoAddressResponseDto.class);

            if (response == null ||
                    response.getDocuments() == null ||
                    response.getDocuments().isEmpty()) {
                log.warn("카카오 좌표 검색 결과 없음 - address={}", address);
                return null;
            }

            KakaoAddressResponseDto.Document document = response.getDocuments().get(0);

            Double longitude = Double.valueOf(document.getX());
            Double latitude = Double.valueOf(document.getY());

            return new Coordinate(latitude, longitude);

        } catch (ResourceAccessException e) {
            log.warn("카카오 좌표 API 연결 실패 - address={}, message={}", address, e.getMessage());
            return null;

        } catch (RestClientException e) {
            log.warn("카카오 좌표 API 호출 실패 - address={}, message={}", address, e.getMessage());
            return null;

        } catch (Exception e) {
            log.warn("좌표 변환 중 예외 발생 - address={}, message={}", address, e.getMessage());
            return null;
        }
    }
}