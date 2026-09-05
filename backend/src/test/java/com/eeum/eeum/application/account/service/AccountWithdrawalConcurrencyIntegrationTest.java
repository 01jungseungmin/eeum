package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.favorite.dto.request.FavoriteToggleRequestDto;
import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.application.used.dto.request.UsedProductCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductImageUploadListRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductImageUploadRequestDto;
import com.eeum.eeum.application.used.service.UsedProductImageService;
import com.eeum.eeum.application.used.service.UsedProductService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.domain.used.event.UsedProductReservationCancelledEvent;
import com.eeum.eeum.domain.used.repository.UsedProductImageRepository;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탈퇴 정리와 같은 사용자의 다른 쓰기가 겹칠 때의 경쟁을 실제 MySQL에서 검증한다.
 *
 * <p><b>이 테스트가 필요한 이유</b> — 탈퇴는 계정 상태 변경으로 끝나지 않는다.
 * {@code AccountWithdrawalProcessor}가 찜을 지우고 대상의 {@code favoriteCount}를 깎으며,
 * 사장이면 상점·상품까지 비활성화한다. 이 정리가 지나간 <b>뒤에</b> 같은 사용자의 쓰기가
 * 커밋되면 정리를 통과한 데이터가 되살아나고 카운터가 어긋난다.
 *
 * <p>방어는 모든 쓰기 경로의 첫 단계인 {@code AccountWriteGuard.lockActive} 하나뿐이다 —
 * 계정 행을 잠그고 {@code assertWritable()}로 상태를 확인한다. 탈퇴 트랜잭션이 계정 행을
 * 쥐고 있으므로 뒤따르는 쓰기는 대기했다가 WITHDRAWN을 보고 멈춘다.
 *
 * <p>단위 테스트는 행 잠금과 커밋 순서를 재현하지 못해 이 경쟁을 잡을 수 없다.
 */
@EnabledIfDockerAvailable
@RecordApplicationEvents
@RequiredArgsConstructor
class AccountWithdrawalConcurrencyIntegrationTest extends IntegrationTestSupport {

    private final AdminAccountService adminAccountService;
    private final FavoriteService favoriteService;
    private final UsedProductService usedProductService;
    private final UsedProductImageService usedProductImageService;
    private final FavoriteRepository favoriteRepository;
    private final UsedProductRepository usedProductRepository;
    private final UsedProductImageRepository usedProductImageRepository;
    private final AccountRepository accountRepository;
    private final NotificationRepository notificationRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final PlatformTransactionManager transactionManager;

    private Long adminId;
    private Long memberId;
    private Long counterpartId;
    private Long categoryId;
    private Long regionId;
    private Long favoritedProductId;
    private Long otherProductId;
    private Long ownProductId;

    @BeforeEach
    void setUp() {
        // 앞선 테스트가 실패해 정리를 마치지 못했을 때 Duplicate entry로 원인이 가려지지 않도록
        // 픽스처 식별자를 매번 다르게 만든다.
        String tag = UUID.randomUUID().toString().substring(0, 8);

        Account admin = accountRepository.save(Account.createUser(
                "admin-" + tag + "@test.com", "encoded_pw", "관리자", "관리자" + tag, "010-0000-0000"));
        Account member = accountRepository.save(Account.createUser(
                "member-" + tag + "@test.com", "encoded_pw", "회원", "회원" + tag, "010-1111-1111"));
        Account seller = accountRepository.save(Account.createUser(
                "seller-" + tag + "@test.com", "encoded_pw", "판매자", "판매자" + tag, "010-2222-2222"));
        adminId = admin.getAccountId();
        memberId = member.getAccountId();
        counterpartId = seller.getAccountId();

        Region region = regionRepository.save(
                Region.create("1168" + tag, "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기" + tag, 1));
        regionId = region.getRegionId();
        categoryId = category.getCategoryId();

        // 중고 게시글 등록은 GPS 인증된 활동 지역을 요구한다. 이게 없으면 탈퇴와 무관하게
        // 등록이 실패해, "탈퇴가 막았다"가 아니라 "원래 안 됐다"를 검증하게 된다.
        accountRegionRepository.save(AccountRegion.builder()
                .account(member)
                .region(region)
                .verified(true)
                .verifiedAt(LocalDateTime.now())
                .build());

        favoritedProductId = saveProduct(seller, category, region, "이미 찜한 글");
        otherProductId = saveProduct(seller, category, region, "탈퇴 중에 찜하려는 글");
        // 탈퇴 대상이 직접 올린 글 — 사진 추가 경쟁에 쓴다.
        ownProductId = saveProduct(member, category, region, "내가 올린 글");

        // 탈퇴 정리가 지울 찜 1건. 정리 후 favoriteCount는 0이어야 한다.
        favoriteService.toggleFavorite(memberId, toggleRequest(favoritedProductId));
    }

    @AfterEach
    void tearDown() {
        chatRoomRepository.deleteAll();
        usedProductImageRepository.deleteAll();
        favoriteRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        // account_region은 account와 region 양쪽을 FK로 잡는다. 먼저 지우지 않으면
        // 두 삭제가 모두 막히고, 공유 DB라 다음 클래스까지 잔여 데이터로 무너진다.
        accountRegionRepository.deleteAll();
        regionRepository.deleteAll();
    }

    @Test
    void 탈퇴_정리_중에_들어온_찜_등록은_정리를_지나쳐_남지_않는다() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();

        raceOnLock(transactionManager, "account",
                () -> adminAccountService.forceDeleteAccount(adminId, memberId),
                () -> favoriteService.toggleFavorite(memberId, toggleRequest(otherProductId)),
                failure);

        // then: 탈퇴한 계정의 쓰기는 거절된다.
        assertThat(failure.get())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        // 잠금이 없으면 정리(deleteAllByAccountId)를 지나친 찜 1건이 남는다.
        // 공유 DB라 전역 count는 다른 클래스가 남긴 데이터에 흔들린다 — 이 픽스처로만 좁혀 본다.
        assertThat(hasFavorite(otherProductId))
                .as("탈퇴 정리 후에 커밋된 찜은 영영 남는다")
                .isFalse();
        assertThat(hasFavorite(favoritedProductId)).isFalse();
        assertThat(favoriteCountOf(otherProductId)).isZero();
        assertThat(favoriteCountOf(favoritedProductId))
                .as("정리 대상 찜의 카운터는 0으로 내려가야 한다")
                .isZero();
    }

    @Test
    void 탈퇴_정리_중에_들어온_중고_게시글_등록은_거절된다() throws Exception {
        long before = usedProductRepository.count();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        raceOnLock(transactionManager, "account",
                () -> adminAccountService.forceDeleteAccount(adminId, memberId),
                () -> usedProductService.create(memberId, createRequest()),
                failure);

        assertThat(failure.get())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        // 탈퇴자의 새 글은 목록에서 걸러지지도 않고(작성 시점엔 계정이 살아 있었으므로) 그대로 남는다.
        assertThat(usedProductRepository.count()).isEqualTo(before);
    }

    @Test
    void 탈퇴_정리_중에_들어온_사진_추가는_거절된다() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();

        raceOnLock(transactionManager, "account",
                () -> adminAccountService.forceDeleteAccount(adminId, memberId),
                () -> usedProductImageService.addImages(memberId, ownProductId, uploadRequest()),
                failure);

        assertThat(failure.get())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        assertThat(usedProductImageRepository.countByUsedProduct_UsedProductId(ownProductId)).isZero();
    }

    @Test
    void 경쟁이_없으면_중고_게시글_등록은_성공한다() {
        // 위 경쟁 테스트가 "탈퇴가 막았다"를 검증하려면, 같은 요청이 평소에는 통과해야 한다.
        // 이 기준선이 없으면 픽스처가 잘못돼 원래부터 실패하는 요청을 검증하게 된다.
        long before = usedProductRepository.count();

        usedProductService.create(memberId, createRequest());

        assertThat(usedProductRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void 탈퇴_처리_자체는_찜과_카운터를_정리하고_커밋된다() {
        // 경쟁이 없을 때의 기준선. 이게 깨지면 위 세 테스트의 "정리를 지나쳤다" 판정도 의미가 없다.
        adminAccountService.forceDeleteAccount(adminId, memberId);

        assertThat(accountRepository.findById(memberId).orElseThrow().isWithdrawn()).isTrue();
        assertThat(hasFavorite(favoritedProductId)).isFalse();
        assertThat(favoriteCountOf(favoritedProductId)).isZero();
    }

    // 공유 DB에서 전역 count()는 다른 클래스의 잔여 데이터에 걸려 거짓 실패를 낸다.
    // IntegrationTestSupport가 경고하는 지점이라, 이 테스트의 픽스처 범위로만 확인한다.
    private boolean hasFavorite(Long productId) {
        return favoriteRepository.existsByAccount_AccountIdAndRefTypeAndRefId(
                memberId, FavoriteRefType.USED_PRODUCT, productId);
    }

    // ─────────────────── 탈퇴 뒷정리 ───────────────────

    @Test
    void 탈퇴하면_예약_중인_거래가_취소되고_구매자에게_통보된다(ApplicationEvents events) {
        // 사용자 삭제 경로는 "상대가 기다리고 있다"는 이유로 RESERVED 삭제를 막는데,
        // 탈퇴는 게시글을 건드리지 않아 예약이 잡힌 채 글만 사라지고 구매자는 통보를 못 받았다.
        // 탈퇴 대상(member)이 판매자이고, 상대는 다른 계정이다.
        // 구매자로 지정하려면 이 상품으로 문의한 이력이 있어야 한다
        chatRoomRepository.save(ChatRoom.createPrivateInquiry(
                accountRepository.findById(counterpartId).orElseThrow(), ownProductId));
        usedProductService.reserve(memberId, ownProductId, counterpartId);

        adminAccountService.forceDeleteAccount(adminId, memberId);

        UsedProduct product = usedProductRepository.findById(ownProductId).orElseThrow();
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.SELLING);
        assertThat(product.getBuyer()).isNull();
        assertThat(events.stream(UsedProductReservationCancelledEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.buyerAccountId()).isEqualTo(counterpartId);
                    assertThat(event.productTitle()).isEqualTo("내가 올린 글");
                });
    }

    @Test
    void 상대가_지정되지_않은_예약은_통보하지_않는다(ApplicationEvents events) {
        // 상대 없이 "예약중" 표시만 해둔 글은 통보할 사람이 없다.
        usedProductService.reserve(memberId, ownProductId, null);

        adminAccountService.forceDeleteAccount(adminId, memberId);

        assertThat(usedProductRepository.findById(ownProductId).orElseThrow().getStatus())
                .isEqualTo(UsedProductStatus.SELLING);
        assertThat(events.stream(UsedProductReservationCancelledEvent.class)).isEmpty();
    }

    @Test
    void 탈퇴하면_기기_토큰이_즉시_지워진다() {
        // 익명화(30일 후)까지 미루면 그동안 탈퇴자 휴대폰으로 푸시가 계속 나간다.
        Account member = accountRepository.findById(memberId).orElseThrow();
        member.updateFcmToken("test-device-token");
        accountRepository.saveAndFlush(member);

        adminAccountService.forceDeleteAccount(adminId, memberId);

        assertThat(accountRepository.findById(memberId).orElseThrow().getFcmToken()).isNull();
    }

    private int favoriteCountOf(Long productId) {
        return usedProductRepository.findById(productId).orElseThrow().getFavoriteCount();
    }

    private Long saveProduct(Account seller, Category category, Region region, String title) {
        return usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region, title, "설명",
                UsedProductPriceType.FIXED, new BigDecimal("10000"))).getUsedProductId();
    }

    private FavoriteToggleRequestDto toggleRequest(Long refId) {
        FavoriteToggleRequestDto request = new FavoriteToggleRequestDto();
        ReflectionTestUtils.setField(request, "refType", FavoriteRefType.USED_PRODUCT);
        ReflectionTestUtils.setField(request, "refId", refId);
        return request;
    }

    private UsedProductCreateRequestDto createRequest() {
        UsedProductCreateRequestDto request = new UsedProductCreateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", categoryId);
        ReflectionTestUtils.setField(request, "regionId", regionId);
        ReflectionTestUtils.setField(request, "title", "탈퇴 중에 올리는 글");
        ReflectionTestUtils.setField(request, "content", "본문");
        ReflectionTestUtils.setField(request, "priceType", UsedProductPriceType.FIXED);
        ReflectionTestUtils.setField(request, "price", new BigDecimal("10000"));
        return request;
    }

    private UsedProductImageUploadListRequestDto uploadRequest() {
        UsedProductImageUploadListRequestDto request = new UsedProductImageUploadListRequestDto();
        UsedProductImageUploadRequestDto image = new UsedProductImageUploadRequestDto();
        ReflectionTestUtils.setField(image, "imageUrl", "withdrawn-race.jpg");
        ReflectionTestUtils.setField(request, "images", List.of(image));
        return request;
    }
}
