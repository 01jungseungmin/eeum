package com.eeum.eeum.infrastructure.external.client;

import com.eeum.eeum.infrastructure.external.config.PublicDataProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicDataApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // data.go.kr의 "Decoding 키"(원문)에는 +, /, = 같은 특수문자가 그대로 들어있다.
    // 이 문자들을 URLEncoder로 미리 인코딩하지 않고 build(true)에 넘기면
    // Spring UriComponentsBuilder가 QUERY_PARAM 검증에서 IllegalArgumentException을 던진다 — 회귀 방지용 테스트.
    @Test
    void Decoding_키에_특수문자가_있어도_예외_없이_인코딩되어_호출된다() {
        // given
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        PublicDataProperties properties = new PublicDataProperties(
                "abcDEF123+/456==", "https://api.example/test", null, null);
        PublicDataApiClient client = new PublicDataApiClient(restClient, objectMapper, properties);

        when(restClient.get().uri(any(URI.class)).retrieve().body(String.class))
                .thenReturn("{\"data\":[]}");

        // when
        List<JsonNode> rows = client.fetchRows(properties.kepcoIndustryUrl(), "TEST");

        // then — 예외 없이 정상적으로 빈 데이터 응답을 처리했는지 확인
        assertThat(rows).isEmpty();

        // .uri(any())는 위 when() 스텁 설정 시 한 번, 실제 fetchRows 호출 시 한 번 총 2회 호출된다.
        // ArgumentCaptor.getValue()는 마지막(=실제 호출) 값을 반환한다.
        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restClient.get(), times(2)).uri(uriCaptor.capture());
        assertThat(uriCaptor.getValue().toString()).contains("serviceKey=abcDEF123%2B%2F456%3D%3D");
    }

    @Test
    void RegionCondition을_전달하면_cond_시군구_EQ_파라미터가_인코딩되어_포함된다() {
        // given
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        PublicDataProperties properties = new PublicDataProperties(
                "test-key", "https://api.example/kepco", null, null);
        PublicDataApiClient client = new PublicDataApiClient(restClient, objectMapper, properties);

        when(restClient.get().uri(any(URI.class)).retrieve().body(String.class))
                .thenReturn("{\"data\":[]}");

        // when
        client.fetchRows(properties.kepcoIndustryUrl(), "KEPCO",
                PublicDataApiClient.RegionCondition.eq("시군구", "강남구"));

        // then
        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restClient.get(), times(2)).uri(uriCaptor.capture());
        String uri = uriCaptor.getValue().toString();
        // cond[시군구::EQ] -> cond%5B%EC%8B%9C%EA%B5%B0%EA%B5%AC%3A%3AEQ%5D, 값 강남구 -> %EA%B0%95%EB%82%A8%EA%B5%AC
        assertThat(uri).contains("cond%5B%EC%8B%9C%EA%B5%B0%EA%B5%AC%3A%3AEQ%5D=%EA%B0%95%EB%82%A8%EA%B5%AC");
    }

    @Test
    void 엔드포인트_URL에_http_스킴이_없으면_호출하지_않고_빈_리스트를_반환한다() {
        // given — aiRestClient는 baseUrl이 없어 scheme 누락 시 "URI is not absolute"로 이어지므로 사전 차단한다
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        PublicDataProperties properties = new PublicDataProperties(
                "test-key", "api.odcloud.kr/api/15156136/v1/uddi:xxxx", null, null);
        PublicDataApiClient client = new PublicDataApiClient(restClient, objectMapper, properties);

        // when
        List<JsonNode> rows = client.fetchRows(properties.kepcoIndustryUrl(), "TEST");

        // then
        assertThat(rows).isEmpty();
        verify(restClient, never()).get();
    }

    @Test
    void 서비스키가_없으면_호출하지_않고_빈_리스트를_반환한다() {
        // given
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        PublicDataProperties properties = new PublicDataProperties(
                "", "https://api.example/test", null, null);
        PublicDataApiClient client = new PublicDataApiClient(restClient, objectMapper, properties);

        // when
        List<JsonNode> rows = client.fetchRows(properties.kepcoIndustryUrl(), "TEST");

        // then
        assertThat(rows).isEmpty();
    }
}
