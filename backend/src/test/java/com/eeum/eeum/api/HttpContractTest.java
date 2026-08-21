package com.eeum.eeum.api;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * HTTP 계약 회귀 테스트 — 잘못된 요청이 400으로 나가는지, 날짜가 어떤 형식으로 직렬화되는지 고정한다.
 * <p>
 * 서비스 단위 테스트는 Controller 앞단(파라미터 변환·역직렬화·예외 매핑)과
 * 응답 직렬화를 전혀 거치지 않으므로 이 계약을 검증하지 못한다.
 * 공개 엔드포인트만 사용해 인증 없이 검사한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class HttpContractTest extends IntegrationTestSupport {


    private final MockMvc mockMvc;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    private Long regionId;

    @BeforeEach
    void setUp() {
        Account seller = accountRepository.save(Account.createUser(
                "seller@test.com", "encoded_pw", "판매자", "판매자닉", "010-2222-2222"));
        Region region = regionRepository.save(
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기", 1));
        regionId = region.getRegionId();

        usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region,
                "자전거 팝니다", "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000")));
    }

    @AfterEach
    void tearDown() {
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void 잘못된_enum_쿼리_파라미터는_400으로_거절한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/used")
                        .param("regionId", String.valueOf(regionId))
                        .param("priceType", "NOT_A_PRICE_TYPE"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 숫자가_아닌_경로변수는_400으로_거절한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/used/not-a-number")).andReturn();

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 필수_쿼리_파라미터가_없으면_400으로_거절한다() throws Exception {
        // refType 없이 호출 (공개 API)
        MvcResult result = mockMvc.perform(get("/favorites/count").param("refId", "1"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 알_수_없는_필드가_섞인_JSON은_500이_아니어야_한다() throws Exception {
        // 계약에서 제거된 필드를 구버전 클라이언트가 계속 보내는 상황.
        // 필수 필드를 비워 두면 역직렬화 성공 시 @Valid가 400을 낸다.
        // 즉 500이 나오면 원인은 검증이 아니라 역직렬화(FAIL_ON_UNKNOWN_PROPERTIES)다.
        // Redis·DB를 타지 않는 지점에서 끝나므로 다른 원인이 섞이지 않는다.
        MvcResult result = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legacyField\":true}"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 문법이_깨진_JSON은_400으로_거절한다() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 오류_응답은_내부_구현_정보를_노출하지_않는다() throws Exception {
        // 파싱 위치·클래스명 같은 원문 메시지를 그대로 내보내면 내부 구조가 드러난다
        MvcResult result = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andReturn();

        assertThat(body(result))
                .doesNotContain("com.eeum.eeum")
                .doesNotContain("line:")
                .doesNotContain("Exception");
    }

    @Test
    void 날짜는_ISO_문자열로_직렬화한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/used")
                        .param("regionId", String.valueOf(regionId)))
                .andReturn();

        String responseBody = body(result);

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), responseBody)
                .isEqualTo(200);
        // JavaTimeModule이 없으면 "2026-08-20T09:00:00"이 아니라 [2026,8,20,9,0,0] 배열로 나간다
        assertThat(responseBody)
                .as("예외: %s / 응답: %s", resolved(result), responseBody)
                .containsPattern("\"createdAt\"\\s*:\\s*\"\\d{4}-\\d{2}-\\d{2}T");
    }

    // MockMvc가 붙잡은 실제 예외 — 500의 원인을 로그 없이 확정한다.
    private String resolved(MvcResult result) {
        Throwable e = result.getResolvedException();
        return e == null ? "없음" : e.getClass().getName() + ": " + e.getMessage();
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }
}
