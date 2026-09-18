package com.eeum.eeum.infrastructure.geo;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 카카오 coord2regioncode 계약 테스트. 실제 카카오 대신 JDK HttpServer 스텁을 쓴다.
 *
 * 요청 형식(x=경도, y=위도, KakaoAK 헤더)과 응답 해석(B 문서만, 리 코드 정규화)을 고정하고,
 * 장애를 "위치 불일치"로 삼키지 않는지 확인한다.
 * 계약 출처: https://developers.kakao.com/docs/latest/ko/local/dev-guide (좌표로 행정구역정보 받기)
 */
@ExtendWith(OutputCaptureExtension.class)
class KakaoRegionCodeClientContractTest {

    private static final String API_KEY = "test-kakao-key";

    // 공식 문서 예시 요청(x=127.1086228, y=37.4012191, 분당구 삼평동)의 응답 구조
    private static final String SAMPYEONG_RESPONSE = """
            {
              "meta": {"total_count": 2},
              "documents": [
                {"region_type": "H", "code": "4113565500", "address_name": "경기도 성남시 분당구 삼평동",
                 "region_1depth_name": "경기도", "region_2depth_name": "성남시 분당구",
                 "region_3depth_name": "삼평동", "region_4depth_name": "",
                 "x": 127.1163593869371, "y": 37.40612091848614},
                {"region_type": "B", "code": "4113510900", "address_name": "경기도 성남시 분당구 삼평동",
                 "region_1depth_name": "경기도", "region_2depth_name": "성남시 분당구",
                 "region_3depth_name": "삼평동", "region_4depth_name": "",
                 "x": 127.10459896729914, "y": 37.40269721785548}
              ]
            }
            """;

    private HttpServer server;
    private KakaoRegionCodeClient client;

    private final AtomicReference<String> capturedMethod = new AtomicReference<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedQuery = new AtomicReference<>();
    private final AtomicReference<String> capturedAuthorization = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    private volatile int responseStatus = 200;
    private volatile String responseBody = SAMPYEONG_RESPONSE;
    private volatile long responseDelayMillis = 0;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        client = clientWithKey(API_KEY);
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    // ─────────────────── 요청 계약 ───────────────────

    @Test
    void 요청은_GET_경로_x경도_y위도_KakaoAK_헤더로_보낸다() {
        // when
        client.findLegalDongCode(37.4012191, 127.1086228);

        // then
        assertThat(capturedMethod.get()).isEqualTo("GET");
        assertThat(capturedPath.get()).isEqualTo("/v2/local/geo/coord2regioncode.json");
        assertThat(capturedQuery.get()).contains("x=127.1086228").contains("y=37.4012191");
        assertThat(capturedAuthorization.get()).isEqualTo("KakaoAK " + API_KEY);
    }

    // ─────────────────── 응답 해석 ───────────────────

    @Test
    void 행정동이_아닌_법정동_코드를_돌려준다() {
        // when
        var code = client.findLegalDongCode(37.4012191, 127.1086228);

        // then
        assertThat(code).contains("4113510900");
    }

    @Test
    void 읍면_지역의_리_단위_코드는_읍면동_코드로_맞춘다() {
        // given
        responseBody = """
                {"meta": {"total_count": 2}, "documents": [
                  {"region_type": "B", "code": "4183025021", "region_4depth_name": "양근리"},
                  {"region_type": "H", "code": "4183025000", "region_4depth_name": ""}
                ]}
                """;

        // when
        var code = client.findLegalDongCode(37.49, 127.49);

        // then
        assertThat(code).contains("4183025000");
    }

    @Test
    void 법정동이_없는_좌표는_빈_값이다() {
        // given
        responseBody = """
                {"meta": {"total_count": 0}, "documents": []}
                """;

        // when
        var code = client.findLegalDongCode(35.0, 140.0);

        // then
        assertThat(code).isEmpty();
    }

    // ─────────────────── 장애를 위치 불일치로 삼키지 않는다 ───────────────────

    @Test
    void 인증_오류_응답은_조회_불가로_던진다() {
        // given
        responseStatus = 401;
        responseBody = """
                {"errorType": "AccessDeniedError", "message": "cannot find appkey"}
                """;

        // when & then
        assertUnavailable(() -> client.findLegalDongCode(37.4012191, 127.1086228));
    }

    @Test
    void 서버_오류_응답은_조회_불가로_던진다() {
        // given
        responseStatus = 500;
        responseBody = "{}";

        // when & then
        assertUnavailable(() -> client.findLegalDongCode(37.4012191, 127.1086228));
    }

    @Test
    void documents가_빠진_응답은_계약_위반으로_조회_불가다() {
        // given
        responseBody = """
                {"meta": {"total_count": 0}}
                """;

        // when & then
        assertUnavailable(() -> client.findLegalDongCode(37.4012191, 127.1086228));
    }

    @Test
    void 법정동_코드_형식이_깨지면_조회_불가다() {
        // given
        responseBody = """
                {"documents": [{"region_type": "B", "code": "41135"}]}
                """;

        // when & then
        assertUnavailable(() -> client.findLegalDongCode(37.4012191, 127.1086228));
    }

    @Test
    void 응답이_읽기_타임아웃을_넘기면_조회_불가이고_로그에_좌표를_남기지_않는다(CapturedOutput output) {
        // given
        responseDelayMillis = 4_000;

        // when & then
        assertUnavailable(() -> client.findLegalDongCode(37.4012191, 127.1086228));
        assertThat(output.getOut()).contains("coord2regioncode 통신 실패")
                .doesNotContain("37.4012191")
                .doesNotContain("127.1086228");
    }

    @Test
    void REST_API_키가_없으면_호출하지_않고_조회_불가다() {
        // given
        KakaoRegionCodeClient noKeyClient = clientWithKey(" ");

        // when & then
        assertUnavailable(() -> noKeyClient.findLegalDongCode(37.4012191, 127.1086228));
        assertThat(requestCount.get()).isZero();
    }

    // ─────────────────── helpers ───────────────────

    private KakaoRegionCodeClient clientWithKey(String apiKey) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new KakaoRegionCodeClient(new KakaoLocalProperties(apiKey, baseUrl));
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REGION_GEOCODE_UNAVAILABLE);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        capturedMethod.set(exchange.getRequestMethod());
        capturedPath.set(exchange.getRequestURI().getPath());
        capturedQuery.set(exchange.getRequestURI().getRawQuery());
        capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));

        if (responseDelayMillis > 0) {
            try {
                Thread.sleep(responseDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        try {
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // 타임아웃 테스트에서는 클라이언트가 먼저 끊는다.
        } finally {
            exchange.close();
        }
    }
}
