package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.response.PortOneCancelResult;
import com.eeum.eeum.config.PortOneProperties;
import com.eeum.eeum.exception.PortOnePaymentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PortOne 취소 API 계약 테스트.
 *
 * <p>실제 PortOne을 호출하지 않고 로컬 HTTP Stub으로 <b>요청 method·path·header·body</b>와
 * <b>응답 JSON → 내부 상태 매핑</b>을 함께 검증한다. DTO fixture만 역직렬화하는 테스트는
 * 어댑터가 실제로 어떤 요청을 보내는지 보호하지 못한다.
 *
 * <p>스텁은 JDK 내장 {@link HttpServer}다 — 이것 때문에 테스트 의존성을 늘리지 않는다.
 *
 * <p><b>여기서 고정하는 계약이 곧 위험 지점이다.</b> 취소 금액 필드명을 잘못 읽으면
 * 금액 대조가 통째로 무력해지고, 반대로 필드가 없다고 실패 처리하면 정상 취소가 전부
 * 수동 검토로 떨어진다. 두 방향을 모두 테스트로 묶어둔다.
 */
class PortOnePaymentClientContractTest {

    private static final String PAYMENT_ID = "payment-1";
    private static final String API_SECRET = "test-api-secret";

    private HttpServer server;
    private PortOnePaymentClientImpl client;

    private final AtomicReference<String> capturedMethod = new AtomicReference<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedAuthorization = new AtomicReference<>();
    private final AtomicReference<String> capturedIdempotencyKey = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();

    private volatile int responseStatus = 200;
    private volatile String responseBody = "{}";

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();

        PortOneProperties properties = new PortOneProperties(
                API_SECRET, "http://127.0.0.1:" + server.getAddress().getPort(), "whsec");
        client = new PortOnePaymentClientImpl(properties, RestClient.create());
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    // ─────────────────── 요청 계약 ───────────────────

    @Test
    void 취소_요청은_약속된_method_path_header_body로_전송된다() throws Exception {
        // given
        givenCancellation("SUCCEEDED", "cancellation-1", "10000");

        // when
        client.cancelPayment(PAYMENT_ID, new BigDecimal("10000"), "고객 요청 취소", "payment-cancel-7");

        // then
        assertThat(capturedMethod.get()).isEqualTo("POST");
        assertThat(capturedPath.get()).isEqualTo("/payments/" + PAYMENT_ID + "/cancel");
        assertThat(capturedAuthorization.get()).isEqualTo("PortOne " + API_SECRET);

        assertThat(capturedIdempotencyKey.get()).isEqualTo("\"payment-cancel-7\"");

        JsonNode body = new ObjectMapper().readTree(capturedBody.get());
        assertThat(body.get("reason").asText()).isEqualTo("고객 요청 취소");
        // 금액은 원 단위 정수로 보낸다 — 소수점이 실린 요청은 PG가 거부한다.
        assertThat(body.get("amount").asLong()).isEqualTo(10000L);
    }

    @Test
    void 멱등키가_없으면_헤더를_보내지_않는다() {
        // given
        givenCancellation("SUCCEEDED", "cancellation-1", "10000");

        // when
        client.cancelPayment(PAYMENT_ID, new BigDecimal("10000"), "사유", null);

        // then
        assertThat(capturedIdempotencyKey.get()).isNull();
    }

    // ─────────────────── 응답 매핑 계약 ───────────────────

    @Test
    void SUCCEEDED_응답은_취소_완료로_매핑된다() {
        // given
        givenCancellation("SUCCEEDED", "cancellation-1", "10000");

        // when
        PortOneCancelResult result = client.cancelPayment(
                PAYMENT_ID, new BigDecimal("10000"), "사유", "key");

        // then
        assertThat(result.isSucceeded()).isTrue();
        assertThat(result.isPending()).isFalse();
        assertThat(result.cancellationId()).isEqualTo("cancellation-1");
        assertThat(result.cancelledAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void REQUESTED_응답은_완료로_확정되지_않는다() {
        // given — 승인 대기 상태다. 돈이 돌아갔다고 말할 수 없다.
        givenCancellation("REQUESTED", "cancellation-2", "10000");

        // when
        PortOneCancelResult result = client.cancelPayment(
                PAYMENT_ID, new BigDecimal("10000"), "사유", "key");

        // then
        assertThat(result.isSucceeded()).isFalse();
        assertThat(result.isPending()).isTrue();
    }

    @Test
    void FAILED_응답은_취소_완료가_아니다() {
        // given
        givenCancellation("FAILED", "cancellation-3", null);

        // when
        PortOneCancelResult result = client.cancelPayment(
                PAYMENT_ID, new BigDecimal("10000"), "사유", "key");

        // then
        assertThat(result.isSucceeded()).isFalse();
        assertThat(result.isPending()).isFalse();
    }

    @Test
    void 부분_취소_금액은_요청_금액과_다르게_읽힌다() {
        // given — 전액 취소를 요청했는데 일부만 취소된 응답
        givenCancellation("SUCCEEDED", "cancellation-4", "3000");

        // when
        PortOneCancelResult result = client.cancelPayment(
                PAYMENT_ID, new BigDecimal("10000"), "사유", "key");

        // then — 호출부가 금액 불일치를 감지할 수 있어야 한다
        assertThat(result.cancelledAmount()).isEqualByComparingTo("3000");
    }

    @Test
    void 금액_필드명이_cancelledAmount여도_읽는다() {
        // given — 문서상 totalAmount가 유력하지만 형태가 바뀌어도 금액을 잃지 않아야 한다
        responseStatus = 200;
        responseBody = """
                {"cancellation":{"id":"cancellation-5","status":"SUCCEEDED","cancelledAmount":10000}}
                """;

        // when
        PortOneCancelResult result = client.cancelPayment(
                PAYMENT_ID, new BigDecimal("10000"), "사유", "key");

        // then
        assertThat(result.cancelledAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void 모르는_필드가_와도_역직렬화가_깨지지_않는다() {
        // given — PortOne이 필드를 추가해도 취소가 멈추면 안 된다
        responseStatus = 200;
        responseBody = """
                {"cancellation":{"id":"c6","status":"SUCCEEDED","totalAmount":10000,
                 "taxFreeAmount":0,"vatAmount":909,"newFieldAddedLater":"x"},"extra":1}
                """;

        // when
        PortOneCancelResult result = client.cancelPayment(
                PAYMENT_ID, new BigDecimal("10000"), "사유", "key");

        // then
        assertThat(result.isSucceeded()).isTrue();
        assertThat(result.cancelledAmount()).isEqualByComparingTo("10000");
    }

    // ─────────────────── 실패 계약 ───────────────────

    @Test
    void cancellation이_없는_응답은_성공으로_넘기지_않는다() {
        // given — 상태를 알 수 없는 응답을 성공 처리하면 취소되지 않은 결제가 취소로 기록된다
        responseStatus = 200;
        responseBody = "{}";

        // when & then
        assertThatThrownBy(() -> client.cancelPayment(
                PAYMENT_ID, new BigDecimal("10000"), "사유", "key"))
                .isInstanceOf(PortOnePaymentException.class);
    }

    @Test
    void HTTP_오류는_PortOne_예외로_변환된다() {
        // given
        responseStatus = 500;
        responseBody = "{\"message\":\"internal error\"}";

        // when & then
        assertThatThrownBy(() -> client.cancelPayment(
                PAYMENT_ID, new BigDecimal("10000"), "사유", "key"))
                .isInstanceOf(PortOnePaymentException.class);
    }

    // ─────────────────── 스텁 ───────────────────

    private void givenCancellation(String status, String id, String amount) {
        responseStatus = 200;
        responseBody = amount == null
                ? "{\"cancellation\":{\"id\":\"" + id + "\",\"status\":\"" + status + "\"}}"
                : "{\"cancellation\":{\"id\":\"" + id + "\",\"status\":\"" + status
                        + "\",\"totalAmount\":" + amount + "}}";
    }

    private void handle(HttpExchange exchange) throws IOException {
        capturedMethod.set(exchange.getRequestMethod());
        capturedPath.set(exchange.getRequestURI().getPath());
        capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        capturedIdempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
        capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
