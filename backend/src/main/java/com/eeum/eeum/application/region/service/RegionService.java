package com.eeum.eeum.application.region.service;

import com.eeum.eeum.application.region.dto.request.RegionApiItemDto;
import com.eeum.eeum.application.region.dto.response.RegionApiResponseDto;
import com.eeum.eeum.config.RestClientConfig;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RegionService {

    private static final int DEFAULT_RADIUS = 1000;
    private static final int NUM_OF_ROWS = 1000;
    private static final String DATA_TYPE = "JSON";

    private final RegionRepository regionRepository;
    private final RestClientConfig restClientConfig;

    @Value("${business.nts.service-key}")
    private String serviceKey;

    public void saveRegion() {
        String encodedServiceKey = URLEncoder.encode(
                serviceKey.trim(),
                StandardCharsets.UTF_8
        );

        RegionApiResponseDto firstResponse = requestRegionApi(
                encodedServiceKey,
                1,
                NUM_OF_ROWS,
                null,
                DATA_TYPE
        );

        if (firstResponse == null ||
                firstResponse.getResponse() == null ||
                firstResponse.getResponse().getBody() == null) {
            log.warn("지역 API 응답이 비어 있습니다.");
            return;
        }

        Integer totalCount = firstResponse.getResponse()
                .getBody()
                .getTotalCount();

        if (totalCount == null || totalCount == 0) {
            log.warn("지역 API totalCount가 없습니다.");
            return;
        }

        int totalPages = (int) Math.ceil((double) totalCount / NUM_OF_ROWS);

        log.info("지역 전체 저장 시작 - totalCount={}, totalPages={}", totalCount, totalPages);

        saveItems(firstResponse);

        for (int pageNo = 2; pageNo <= totalPages; pageNo++) {
            RegionApiResponseDto response = requestRegionApi(
                    encodedServiceKey,
                    pageNo,
                    NUM_OF_ROWS,
                    null,
                    DATA_TYPE
            );

            saveItems(response);

            log.info("지역 저장 진행 중 - pageNo={}/{}", pageNo, totalPages);
        }

        log.info("지역 전체 저장 완료");
    }

    private RegionApiResponseDto requestRegionApi(
            String encodedServiceKey,
            int pageNo,
            int numOfRows,
            String ctpvCd,
            String dataType
    ) {
        String url = "https://apis.data.go.kr/1613000/RegionalCode/getRegionalCode"
                + "?serviceKey=" + encodedServiceKey
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows
                + "&dataType=" + dataType;

        if (ctpvCd != null && !ctpvCd.isBlank()) {
            url += "&ctpv_cd=" + ctpvCd;
        }

        log.info("지역 API 요청 URL = {}", url);

        return restClientConfig.restClient()
                .get()
                .uri(URI.create(url))
                .retrieve()
                .body(RegionApiResponseDto.class);
    }

    private void saveItems(RegionApiResponseDto response) {
        if (response == null ||
                response.getResponse() == null ||
                response.getResponse().getBody() == null ||
                response.getResponse().getBody().getItems() == null ||
                response.getResponse().getBody().getItems().getItem() == null) {
            return;
        }

        List<RegionApiItemDto> items = response.getResponse()
                .getBody()
                .getItems()
                .getItem();

        log.info("지역 API item 개수 = {}", items.size());

        for (RegionApiItemDto item : items) {
            saveRegionIfValid(item);
        }
    }

    private void saveRegionIfValid(RegionApiItemDto item) {
        if (!"Y".equals(item.getUseYn())) {
            return;
        }

        if (!"3".equals(item.getRgnSe())) {
            return;
        }

        if (item.getEmdNm() == null) {
            return;
        }

        String regionCode = item.getEmdCd();

        if (regionRepository.existsByRegionCode(regionCode)) {
            return;
        }

        Region region = Region.create(
                regionCode,
                item.getCtpvNm(),
                item.getSggNm(),
                item.getEmdNm(),
                DEFAULT_RADIUS
        );

        Region savedRegion = regionRepository.save(region);

        log.info("지역 저장 완료 - id={}, regionCode={}, name={}",
                savedRegion.getRegionId(),
                savedRegion.getRegionCode(),
                savedRegion.getFullName()
        );
    }
}