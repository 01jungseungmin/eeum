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
import com.eeum.eeum.domain.used.entity.UsedReview;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.domain.used.repository.UsedReviewRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
    private final UsedReviewRepository usedReviewRepository;
    private final AccountRepository accountRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    private Long regionId;
    private Long sellerId;
    private final List<Long> reviewIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Account seller = accountRepository.save(Account.createUser(
                "seller@test.com", "encoded_pw", "판매자", "판매자닉", "010-2222-2222"));
        Region region = regionRepository.save(
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기", 1));
        regionId = region.getRegionId();
        sellerId = seller.getAccountId();

        usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region,
                "자전거 팝니다", "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000")));

        // 커서 왕복을 검증하려면 한 페이지를 넘기는 목록이 필요하다. 후기는 거래당 1건이라
        // 판매완료 게시글을 3개 만들고 같은 구매자가 하나씩 남긴다.
        Account buyer = accountRepository.save(Account.createUser(
                "buyer@test.com", "encoded_pw", "구매자", "구매자닉", "010-3333-3333"));
        reviewIds.clear();
        for (int i = 0; i < 3; i++) {
            UsedProduct sold = UsedProduct.create(
                    seller, category, region, "판매완료" + i, "내용",
                    UsedProductPriceType.FIXED, new BigDecimal("20000"));
            sold.markSold(buyer);
            usedProductRepository.saveAndFlush(sold);
            reviewIds.add(usedReviewRepository.saveAndFlush(
                    UsedReview.create(sold, buyer, 5, "후기" + i)).getUsedReviewId());
        }
    }

    @AfterEach
    void tearDown() {
        usedReviewRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
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
    void 음수_가격_검색은_400으로_거절한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/used")
                        .param("regionId", String.valueOf(regionId))
                        .param("minPrice", "-1000"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 자릿수를_넘는_가격_검색은_400으로_거절한다() throws Exception {
        // 컬럼이 DECIMAL(10,2)다. 등록 요청과 같은 경계를 검색에도 적용한다.
        MvcResult result = mockMvc.perform(get("/used")
                        .param("regionId", String.valueOf(regionId))
                        .param("maxPrice", "12345678901234"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 최소가_최대보다_큰_가격_검색은_400으로_거절한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/used")
                        .param("regionId", String.valueOf(regionId))
                        .param("minPrice", "50000")
                        .param("maxPrice", "1000"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
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

    // ===================== 커서 페이징 계약 =====================
    //
    // 커서 파라미터는 컨트롤러 앞단(파라미터 바인딩·@Positive/@Max·예외 매핑)에서 걸린다.
    // 서비스 단위 테스트는 그 구간을 통과하지 않아 이 계약을 검증하지 못한다.

    @Test
    void 커서를_한쪽만_보내면_400으로_거절한다() throws Exception {
        // 조용히 첫 페이지를 돌려주면 클라이언트는 다음 페이지를 받았다고 믿어
        // 무한 스크롤이 같은 목록을 반복한다.
        MvcResult valueOnly = mockMvc.perform(get("/used/sellers/" + sellerId + "/reviews")
                        .param("cursorValue", "2026-09-01T10:00:00"))
                .andReturn();
        MvcResult idOnly = mockMvc.perform(get("/used/sellers/" + sellerId + "/reviews")
                        .param("cursorId", "41"))
                .andReturn();

        assertThat(valueOnly.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(valueOnly), body(valueOnly))
                .isEqualTo(400);
        assertThat(idOnly.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(idOnly), body(idOnly))
                .isEqualTo(400);
    }

    @Test
    void 형식이_깨진_커서_값은_400으로_거절한다() throws Exception {
        // 응답의 nextCursorValue를 그대로 되돌려보내는 계약이므로,
        // 형식이 깨졌다는 것은 클라이언트가 값을 직접 만들었다는 뜻이다.
        MvcResult result = mockMvc.perform(get("/used/sellers/" + sellerId + "/reviews")
                        .param("cursorValue", "어제")
                        .param("cursorId", "41"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 커서_ID가_0이면_400으로_거절한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/used/sellers/" + sellerId + "/reviews")
                        .param("cursorValue", "2026-09-01T10:00:00")
                        .param("cursorId", "0"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
    }

    @Test
    void 허용_범위를_벗어난_size는_400으로_거절한다() throws Exception {
        // 상한이 없으면 한 번의 요청이 목록 전체를 끌어온다.
        MvcResult zero = mockMvc.perform(get("/used/sellers/" + sellerId + "/reviews")
                        .param("size", "0"))
                .andReturn();
        MvcResult tooLarge = mockMvc.perform(get("/used/sellers/" + sellerId + "/reviews")
                        .param("size", "51"))
                .andReturn();

        assertThat(zero.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(zero), body(zero))
                .isEqualTo(400);
        assertThat(tooLarge.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(tooLarge), body(tooLarge))
                .isEqualTo(400);
    }

    @Test
    void 후기_목록은_받은_커서로_이어_읽히고_마지막_페이지는_커서를_비운다() throws Exception {
        // 응답이 준 커서를 그대로 되돌려보내는 것이 클라이언트가 할 일의 전부여야 한다.
        MvcResult first = mockMvc.perform(get("/used/sellers/" + sellerId + "/reviews")
                        .param("size", "2"))
                .andReturn();

        String firstBody = body(first);
        assertThat(first.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(first), firstBody)
                .isEqualTo(200);
        boolean firstHasNext = JsonPath.read(firstBody, "$.data.hasNext");
        assertThat(firstHasNext).isTrue();
        assertThat(JsonPath.read(firstBody, "$.data.content").toString())
                .as("첫 페이지는 최신 후기 2건이다")
                .contains("후기2").contains("후기1").doesNotContain("후기0");

        // 페이지 번호 계약을 섞지 않는다 — Slice로 돌리면 두 번째 페이지도 number=0, first=true다.
        assertThat(firstBody)
                .doesNotContain("\"first\"")
                .doesNotContain("\"pageable\"")
                .doesNotContain("\"number\"");
        // 정렬은 상태가 아니라 실제 필드·방향으로 나가야 한다.
        assertThat(JsonPath.read(firstBody, "$.data.sort").toString())
                .isEqualTo("[\"createdAt,DESC\",\"usedReviewId,DESC\"]");

        // 지역 변수로 받는다 — param(String, String...)에 바로 넘기면 제네릭이 String[]로 추론된다.
        String nextCursorValue = JsonPath.read(firstBody, "$.data.nextCursorValue");
        Number nextCursorId = JsonPath.read(firstBody, "$.data.nextCursorId");

        MvcResult second = mockMvc.perform(get("/used/sellers/" + sellerId + "/reviews")
                        .param("size", "2")
                        .param("cursorValue", nextCursorValue)
                        .param("cursorId", String.valueOf(nextCursorId.longValue())))
                .andReturn();

        String secondBody = body(second);
        assertThat(second.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(second), secondBody)
                .isEqualTo(200);
        // 이미 본 후기가 다시 오면 안 되고, 마지막 페이지는 커서를 비워야 한다 —
        // 남겨두면 클라이언트가 빈 페이지를 한 번 더 요청한다.
        assertThat(JsonPath.read(secondBody, "$.data.content").toString())
                .contains("후기0").doesNotContain("후기1").doesNotContain("후기2");
        boolean secondHasNext = JsonPath.read(secondBody, "$.data.hasNext");
        assertThat(secondHasNext).isFalse();
        Object lastPageCursorValue = JsonPath.read(secondBody, "$.data.nextCursorValue");
        Object lastPageCursorId = JsonPath.read(secondBody, "$.data.nextCursorId");
        assertThat(lastPageCursorValue).isNull();
        assertThat(lastPageCursorId).isNull();
    }

    @Test
    void 빈_커서_값만_보내도_400으로_거절한다() throws Exception {
        // ?cursorValue= 를 첫 페이지로 처리하면, 커서를 보냈다고 믿는 클라이언트가
        // 같은 목록을 계속 다시 받는다.
        MvcResult result = mockMvc.perform(get("/used/sellers/" + sellerId + "/reviews")
                        .param("cursorValue", ""))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("예외: %s / 응답: %s", resolved(result), body(result))
                .isEqualTo(400);
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
