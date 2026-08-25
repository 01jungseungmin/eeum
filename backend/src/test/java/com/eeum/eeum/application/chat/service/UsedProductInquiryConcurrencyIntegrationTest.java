package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.dto.response.ChatRoomResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 중고 문의방 동시 생성 경쟁 검증.
 *
 * <p>"채팅하기" 연타나 재시도로 같은 요청이 겹쳐도 활성 방은 하나여야 하고, 뒤에 온 요청은
 * 오류가 아니라 <b>같은 방</b>을 받아야 한다.
 *
 * <p><b>이 테스트가 실제로 증명하는 것</b> — 같은 구매자의 동시 요청은 쓰기 경로 첫 단계인
 * 계정 행 잠금({@code AccountWriteGuard.lockActive})에서 직렬화되고, 뒤이은 요청이 기존 방을
 * 그대로 돌려받는다는 것이다. 되돌림 검증으로 확인한 결과 상품 행 잠금이나 생성 컬럼 분기를
 * 제거해도 이 테스트는 통과한다 — 계정 잠금이 먼저 막기 때문이다.
 *
 * <p>따라서 유일성 제약 자체는 여기서 검증되지 않는다. 그쪽은 서비스를 거치지 않고 같은 조합을
 * 직접 INSERT하는 {@code UsedProductInquiryRoomIntegrationTest}의 스키마 제약 테스트가 맡는다.
 * 둘을 합쳐야 "계정 잠금이 정상 경로를 직렬화하고, 그것이 뚫려도 DB가 막는다"가 고정된다.
 *
 * <p>단위 테스트는 행 잠금과 유니크 위반을 재현하지 못해 이 경쟁을 잡을 수 없다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class UsedProductInquiryConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final int CONCURRENCY = 6;

    private final ChatRoomService chatRoomService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    private Long buyerId;
    private Long secondBuyerId;
    private Long productId;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);

        Account seller = accountRepository.save(Account.createUser(
                "c-seller-" + tag + "@test.com", "pw", "판매자", "판매자" + tag, "010-2222-2222"));
        Account buyer = accountRepository.save(Account.createUser(
                "c-buyer-" + tag + "@test.com", "pw", "구매자", "구매자" + tag, "010-1111-1111"));
        Account secondBuyer = accountRepository.save(Account.createUser(
                "c-buyer2-" + tag + "@test.com", "pw", "구매자2", "구매자2" + tag, "010-3333-3333"));
        buyerId = buyer.getAccountId();
        secondBuyerId = secondBuyer.getAccountId();

        Region region = regionRepository.save(
                Region.create("1168" + tag, "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기" + tag, 1));

        for (Account account : List.of(buyer, secondBuyer)) {
            accountRegionRepository.save(AccountRegion.builder()
                    .account(account).region(region)
                    .verified(true).verifiedAt(LocalDateTime.now()).build());
        }

        productId = usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region, "자전거 팝니다", "설명",
                UsedProductPriceType.FIXED, new BigDecimal("10000"))).getUsedProductId();
    }

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAll();
        chatParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRegionRepository.deleteAll();
        regionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void 같은_구매자의_동시_문의는_방을_하나만_만들고_모두_같은_방을_받는다() throws Exception {
        List<ChatRoomResponseDto> results = raceInquiries(buyerId, CONCURRENCY);

        // 모든 요청이 성공해야 한다 — 뒤에 온 요청을 오류로 떨어뜨리면 멱등 계약이 깨진다.
        assertThat(results).hasSize(CONCURRENCY);

        // 방은 하나, 그리고 전원이 같은 방을 받았어야 한다.
        assertThat(activeRooms()).hasSize(1);
        assertThat(results).extracting(ChatRoomResponseDto::getRoomId)
                .containsOnly(activeRooms().get(0).getChatroomId());
    }

    @Test
    void 동시_생성_후에도_참여자는_구매자와_판매자_둘뿐이다() throws Exception {
        raceInquiries(buyerId, CONCURRENCY);

        Long roomId = activeRooms().get(0).getChatroomId();
        assertThat(chatParticipantRepository.findActiveAccountIds(roomId))
                .as("경쟁 중 참여자가 중복 INSERT되면 이후 참여자 검증이 전부 500이 된다")
                .hasSize(2);
    }

    @Test
    void 구매자가_다르면_동시_문의라도_각자의_방이_생긴다() throws Exception {
        // 유일성 단위는 "상품 + 구매자"다. 상품 단위로 막으면 정상 요청이 서로를 밀어낸다.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ChatRoomResponseDto>> futures = new ArrayList<>();
            for (Long buyer : List.of(buyerId, secondBuyerId)) {
                futures.add(executor.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    return chatRoomService.createUsedProductInquiry(buyer, productId);
                }));
            }
            start.countDown();
            for (Future<ChatRoomResponseDto> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(activeRooms())
                .as("구매자별로 방이 하나씩 있어야 한다")
                .hasSize(2)
                .extracting(ChatRoom::getBuyerAccountId)
                .containsExactlyInAnyOrder(buyerId, secondBuyerId);
    }

    // 같은 요청을 동시에 쏘고 결과를 모은다. 하나라도 실패하면 그 예외가 그대로 올라온다.
    private List<ChatRoomResponseDto> raceInquiries(Long buyer, int count) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<ChatRoomResponseDto> results = new ArrayList<>();

        try {
            List<Future<ChatRoomResponseDto>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    return chatRoomService.createUsedProductInquiry(buyer, productId);
                }));
            }
            start.countDown();   // 동시에 출발시킨다

            for (Future<ChatRoomResponseDto> future : futures) {
                try {
                    results.add(future.get(30, TimeUnit.SECONDS));
                } catch (Exception e) {
                    failure.compareAndSet(null, e.getCause() != null ? e.getCause() : e);
                }
            }
        } finally {
            executor.shutdownNow();
        }

        if (failure.get() != null) {
            throw new AssertionError(
                    "동시 문의 요청이 실패했다 — 뒤에 온 요청도 기존 방을 받아야 한다: " + failure.get(),
                    failure.get());
        }
        return results;
    }

    private List<ChatRoom> activeRooms() {
        return chatRoomRepository.findAll().stream()
                .filter(room -> room.isUsedProductRoom()
                        && room.getRefId().equals(productId)
                        && room.isActive())
                .toList();
    }
}
