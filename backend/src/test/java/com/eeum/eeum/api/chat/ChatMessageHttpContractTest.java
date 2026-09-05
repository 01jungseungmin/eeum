package com.eeum.eeum.api.chat;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.security.jwt.JwtProvider;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 채팅 메시지 API의 파라미터 검증 계약.
 *
 * <p>인증이 필요한 경로라 공개 엔드포인트만 쓰는 {@code HttpContractTest}에 둘 수 없다.
 *
 * <p>서비스 단위 테스트는 컨트롤러 앞단(파라미터 바인딩, {@code @Validated} 메서드 검증,
 * 예외 매핑)을 지나가지 않으므로 이 계약을 검증하지 못한다. 상한이 없으면 인증된 사용자가
 * size에 큰 값을 넣어 한 번의 요청으로 방 전체 메시지를 끌어올 수 있다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class ChatMessageHttpContractTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final AccountRepository accountRepository;
    private final JwtProvider jwtProvider;

    private String accessToken;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Account account = accountRepository.save(Account.createUser(
                "chat-contract-" + tag + "@test.com", "encoded_pw", "계약", "계약" + tag,
                "010-7777-8888"));
        accessToken = jwtProvider.generateAccessToken(
                account.getAccountId(), account.getRole().name(), 0L);
    }

    @Test
    void 상한을_넘는_size는_400으로_거절한다() {
        MvcResult result = call(get("/chat/rooms/1/messages").param("size", "101"));

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void size가_0_이하이면_400으로_거절한다() {
        MvcResult result = call(get("/chat/rooms/1/messages").param("size", "0"));

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 음수_방_ID는_400으로_거절한다() {
        // 존재하지 않는 방이라 404가 될 수도 있지만, 그 전에 형식으로 걸러야 한다.
        MvcResult result = call(get("/chat/rooms/-1/messages"));

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 음수_메시지_ID는_400으로_거절한다() {
        MvcResult result = call(delete("/chat/messages/-1"));

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 형식이_깨진_커서_값은_400으로_거절한다() {
        MvcResult result = call(get("/chat/rooms/1/messages")
                .param("cursorValue", "어제")
                .param("cursorId", "41"));

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 메시지_커서를_한쪽만_보내면_400으로_거절한다() {
        // 시각만 받으면 같은 시각에 저장된 메시지 사이에서 경계를 끊지 못한다.
        // 조용히 첫 페이지를 돌려주면 화면에 같은 목록이 다시 쌓인다.
        MvcResult valueOnly = call(get("/chat/rooms/1/messages")
                .param("cursorValue", "2026-09-01T10:00:00"));
        MvcResult idOnly = call(get("/chat/rooms/1/messages").param("cursorId", "41"));

        assertThat(valueOnly.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(valueOnly), body(valueOnly))
                .isEqualTo(400);
        assertThat(idOnly.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(idOnly), body(idOnly))
                .isEqualTo(400);
    }

    private MvcResult call(MockHttpServletRequestBuilder builder) {
        try {
            return mockMvc.perform(builder.header("Authorization", "Bearer " + accessToken))
                    .andReturn();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // MockMvc가 붙잡은 실제 예외 — 500의 원인을 로그 없이 확정한다.
    private String resolved(MvcResult result) {
        Throwable e = result.getResolvedException();
        return e == null ? "없음" : e.getClass().getName() + ": " + e.getMessage();
    }

    private String body(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (Exception e) {
            return "(본문 읽기 실패)";
        }
    }
}
