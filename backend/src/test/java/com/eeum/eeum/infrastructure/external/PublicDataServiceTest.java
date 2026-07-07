package com.eeum.eeum.infrastructure.external;

import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.infrastructure.external.client.PublicDataApiClient;
import com.eeum.eeum.infrastructure.external.config.PublicDataProperties;
import com.eeum.eeum.infrastructure.external.dto.GasAccidentStat;
import com.eeum.eeum.infrastructure.external.dto.RegionPowerUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicDataServiceTest {

    @Mock private PublicDataApiClient apiClient;
    @Mock private RedisUtil redisUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PublicDataService createService() {
        lenient().when(redisUtil.get(anyString())).thenReturn(Optional.empty());
        PublicDataProperties properties = new PublicDataProperties(
                "test-key", "https://api.example/kepco", "https://api.example/kpx", "https://api.example/kgs");
        return new PublicDataService(apiClient, properties, redisUtil, objectMapper);
    }

    private List<JsonNode> rows(String json) throws Exception {
        JsonNode array = objectMapper.readTree(json);
        java.util.ArrayList<JsonNode> list = new java.util.ArrayList<>();
        array.forEach(list::add);
        return list;
    }

    @Test
    void 산업분류별_전력사용량_정상_응답을_정규화한다() throws Exception {
        // given
        PublicDataService service = createService();
        when(apiClient.fetchRows(anyString(), anyString())).thenReturn(rows("""
                [{"기준년월":"202605","시군구":"마포구","산업분류":"음식점업","판매량":"12345.6","고객호수":"120"}]"""));

        // when
        List<RegionPowerUsage> usages = service.getIndustryPowerUsages("마포구");

        // then
        assertThat(usages).hasSize(1);
        RegionPowerUsage usage = usages.get(0);
        assertThat(usage.usageKwh()).isEqualTo(12345.6);
        assertThat(usage.customerCount()).isEqualTo(120L);
        assertThat(usage.averageUsageKwh()).isNotNull();
    }

    @Test
    void 지역_키워드와_일치하지_않는_행은_제외된다() throws Exception {
        // given
        PublicDataService service = createService();
        when(apiClient.fetchRows(anyString(), anyString())).thenReturn(rows("""
                [{"기준년월":"202605","시군구":"강남구","판매량":"999"}]"""));

        // when
        List<RegionPowerUsage> usages = service.getIndustryPowerUsages("마포구");

        // then
        assertThat(usages).isEmpty();
    }

    @Test
    void API_실패나_키_누락_시_빈_결과로_fallback한다() {
        // given — 클라이언트가 실패 시 빈 리스트 반환 (500으로 전파되지 않음)
        PublicDataService service = createService();
        when(apiClient.fetchRows(anyString(), anyString())).thenReturn(List.of());

        // when & then
        assertThat(service.getIndustryPowerUsages("마포구")).isEmpty();
        assertThat(service.getRegionEnergyUsages("마포구")).isEmpty();
        assertThat(service.getGasAccidentSummary()).isEmpty();
    }

    @Test
    void 가스사고_요약은_총_건수와_최다_원인을_계산한다() throws Exception {
        // given
        PublicDataService service = createService();
        when(apiClient.fetchRows(anyString(), anyString())).thenReturn(rows("""
                [{"발생년월":"202605","사고원인":"시설미비","사고건수":"3"},
                 {"발생년월":"202605","사고원인":"취급부주의","사고건수":"7"}]"""));

        // when
        Optional<PublicDataService.GasAccidentSummary> summary = service.getGasAccidentSummary();

        // then
        assertThat(summary).isPresent();
        assertThat(summary.get().totalCount()).isEqualTo(10);
        assertThat(summary.get().topCause()).isEqualTo("취급부주의");
    }

    @Test
    void 정상_조회_결과는_캐시에_저장된다() throws Exception {
        // given
        PublicDataService service = createService();
        when(apiClient.fetchRows(anyString(), anyString())).thenReturn(rows("""
                [{"기준년월":"202605","시군구":"마포구","판매량":"100"}]"""));

        // when
        service.getIndustryPowerUsages("마포구");

        // then
        org.mockito.Mockito.verify(redisUtil).set(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }
}
