package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.dto.response.ChatRoomDetailResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomResponseDto;
import com.eeum.eeum.application.chat.dto.response.UsedProductChatSummaryDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.entity.UsedProductImage;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductImageRepository;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.support.SqlCaptureInspector;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채팅 화면에 붙는 중고 게시글 요약 검증.
 *
 * <p>1:1 문의방은 이름을 두지 않으므로, 이 요약이 없으면 사용자는 목록에서 어떤 방인지 구분할 수
 * 없다. 즉 표시용 부가 정보가 아니라 문의방을 쓸 수 있게 만드는 필수 값이다.
 *
 * <p>방마다 상품을 조회하면 페이지 크기만큼 쿼리가 늘어난다. 배치 조회 여부는 실제 실행 SQL로만
 * 확인할 수 있어 통합 테스트로 고정한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class UsedProductInquiryRoomViewIntegrationTest extends IntegrationTestSupport {

    private final ChatRoomService chatRoomService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UsedProductRepository usedProductRepository;
    private final UsedProductImageRepository usedProductImageRepository;
    private final AccountRepository accountRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    private Long buyerId;
    private Long productId;
    private Account seller;
    private Region region;
    private Category category;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);

        seller = accountRepository.save(Account.createUser(
                "v-seller-" + tag + "@test.com", "pw", "판매자", "판매자" + tag, "010-2222-2222"));
        Account buyer = accountRepository.save(Account.createUser(
                "v-buyer-" + tag + "@test.com", "pw", "구매자", "구매자" + tag, "010-1111-1111"));
        buyerId = buyer.getAccountId();

        region = regionRepository.save(Region.create("1168" + tag, "서울특별시", "강남구", "역삼동", 3));
        category = categoryRepository.save(Category.createRoot(CategoryType.USED, "디지털기기" + tag, 1));

        accountRegionRepository.save(AccountRegion.builder()
                .account(buyer).region(region).verified(true).verifiedAt(LocalDateTime.now()).build());

        productId = saveProduct("자전거 팝니다", new BigDecimal("10000"));
        saveThumbnail(productId, "https://cdn.test/bike.jpg");
    }

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAll();
        chatParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
        usedProductImageRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRegionRepository.deleteAll();
        regionRepository.deleteAll();
    }

    @Test
    void 문의방_목록은_어떤_게시글의_대화인지_담아_돌려준다() {
        chatRoomService.createUsedProductInquiry(buyerId, productId);

        UsedProductChatSummaryDto summary = firstRoomSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.getUsedProductId()).isEqualTo(productId);
        assertThat(summary.getTitle()).isEqualTo("자전거 팝니다");
        assertThat(summary.getThumbnailUrl()).isEqualTo("https://cdn.test/bike.jpg");
        assertThat(summary.getPriceType()).isEqualTo(UsedProductPriceType.FIXED);
        assertThat(summary.getPrice()).isEqualByComparingTo("10000");
        assertThat(summary.isVisible()).isTrue();
    }

    @Test
    void 문의방_상세도_같은_요약을_담는다() {
        ChatRoomResponseDto created = chatRoomService.createUsedProductInquiry(buyerId, productId);

        ChatRoomDetailResponseDto detail =
                chatRoomService.getRoomDetail(buyerId, created.getRoomId());

        assertThat(detail.getUsedProduct()).isNotNull();
        assertThat(detail.getUsedProduct().getUsedProductId()).isEqualTo(productId);
    }

    @Test
    void 생성_응답에도_요약이_들어간다() {
        // 상품 상세에서 "채팅하기"로 들어온 직후 화면이 바로 상품을 표시할 수 있어야 한다.
        ChatRoomResponseDto created = chatRoomService.createUsedProductInquiry(buyerId, productId);

        assertThat(created.getUsedProduct()).isNotNull();
        assertThat(created.getUsedProduct().getTitle()).isEqualTo("자전거 팝니다");
    }

    @Test
    void 숨겨진_게시글은_이동_불가로_표시된다() {
        // 삭제만 보면 숨겨진 게시글이 정상 글로 내려가 프론트가 이동을 막지 않고,
        // 구매자가 탭하면 상세에서 404를 만난다. 판정은 세 축 전체여야 한다.
        chatRoomService.createUsedProductInquiry(buyerId, productId);
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        product.hide();
        usedProductRepository.saveAndFlush(product);

        UsedProductChatSummaryDto summary = firstRoomSummary();

        assertThat(summary.isVisible()).isFalse();
        // 당사자에게 제목까지 감추지는 않는다 — 그 게시글을 보고 대화를 시작한 사람들이다.
        assertThat(summary.getTitle()).isEqualTo("자전거 팝니다");
    }

    @Test
    void 삭제된_게시글도_이동_불가_표시와_함께_담는다() {
        // 기존 대화는 유지하는 정책이라, 요약을 빼면 "삭제된 게시글입니다"를 표시할 수 없다.
        chatRoomService.createUsedProductInquiry(buyerId, productId);
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        product.softDelete();
        usedProductRepository.saveAndFlush(product);

        UsedProductChatSummaryDto summary = firstRoomSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.isVisible()).isFalse();
        assertThat(summary.getTitle()).isEqualTo("자전거 팝니다");
    }

    @Test
    void 사진이_없는_게시글은_썸네일이_null이다() {
        Long noImageProductId = saveProduct("사진 없는 글", new BigDecimal("5000"));
        chatRoomService.createUsedProductInquiry(buyerId, noImageProductId);

        UsedProductChatSummaryDto summary = summaryOf(noImageProductId);

        assertThat(summary).isNotNull();
        assertThat(summary.getThumbnailUrl()).isNull();
    }

    @Test
    void 문의방이_여러_개여도_상품과_썸네일은_각각_한_번만_조회한다() {
        // 방마다 조회하면 페이지 크기만큼 쿼리가 늘어난다(N+1).
        for (int i = 0; i < 3; i++) {
            Long id = saveProduct("상품" + i, new BigDecimal("1000"));
            saveThumbnail(id, "https://cdn.test/" + i + ".jpg");
            chatRoomService.createUsedProductInquiry(buyerId, id);
        }
        SqlCaptureInspector.reset();

        Slice<ChatRoomResponseDto> rooms =
                chatRoomService.getMyRooms(buyerId, PageRequest.of(0, 20), false);

        assertThat(rooms.getContent()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(selectsFrom("used_product_image"))
                .as("대표 사진 조회: %s", selectsFrom("used_product_image"))
                .hasSize(1);
        assertThat(selectsFrom("used_product "))
                .as("게시글 조회: %s", selectsFrom("used_product "))
                .hasSize(1);
        // 요약의 visible 판정이 판매자 상태를 본다. fetch join이 빠지면
        // 게시글 수만큼 판매자 조회가 더 나간다.
        assertThat(selectsFrom("account"))
                .as("판매자 조회: %s", selectsFrom("account"))
                .isEmpty();
    }

    private List<String> selectsFrom(String table) {
        return SqlCaptureInspector.captured().stream()
                .map(String::toLowerCase)
                .filter(sql -> sql.startsWith("select"))
                .filter(sql -> sql.contains("from " + table))
                .toList();
    }

    private UsedProductChatSummaryDto firstRoomSummary() {
        return chatRoomService.getMyRooms(buyerId, PageRequest.of(0, 20), false)
                .getContent().stream()
                .map(ChatRoomResponseDto::getUsedProduct)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private UsedProductChatSummaryDto summaryOf(Long usedProductId) {
        return chatRoomService.getMyRooms(buyerId, PageRequest.of(0, 20), false)
                .getContent().stream()
                .map(ChatRoomResponseDto::getUsedProduct)
                .filter(java.util.Objects::nonNull)
                .filter(summary -> summary.getUsedProductId().equals(usedProductId))
                .findFirst()
                .orElse(null);
    }

    private Long saveProduct(String title, BigDecimal price) {
        return usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region, title, "설명",
                UsedProductPriceType.FIXED, price)).getUsedProductId();
    }

    private void saveThumbnail(Long usedProductId, String url) {
        UsedProduct product = usedProductRepository.findById(usedProductId).orElseThrow();
        usedProductImageRepository.saveAndFlush(
                UsedProductImage.create(product, url, 1, true));
    }
}
