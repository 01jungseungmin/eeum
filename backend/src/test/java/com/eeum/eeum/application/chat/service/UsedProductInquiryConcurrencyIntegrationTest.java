package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.dto.request.ChatMessageSendRequestDto;
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
import com.eeum.eeum.domain.notification.entity.Notification;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.notification.enums.OutboxStatus;
import com.eeum.eeum.domain.notification.repository.NotificationOutboxRepository;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
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
    private final PlatformTransactionManager transactionManager;
    private final ChatMessageService chatMessageService;
    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;

    private Long buyerId;
    private Long secondBuyerId;
    private Long sellerId;
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
        sellerId = seller.getAccountId();

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
        notificationOutboxRepository.deleteAll();
        chatMessageRepository.deleteAll();
        chatParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRegionRepository.deleteAll();
        regionRepository.deleteAll();
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
    /**
     * 판매자 탈퇴와 문의 시작의 경쟁.
     *
     * <p>문의 생성은 구매자 계정과 상품만 잠갔고, 탈퇴는 판매자 계정과 RESERVED 상품만 잠근다.
     * SELLING 상품에서는 두 경로가 한 행도 공유하지 않아, 공개 상태를 확인한 직후 탈퇴가 커밋되면
     * <b>탈퇴 정리가 끝난 뒤에</b> 그 판매자가 참여자인 ACTIVE 방이 생긴다.
     *
     * <p>이 테스트는 문의가 판매자 계정 행 잠금을 <b>기다리는지</b>까지 단언한다
     * ({@code raceOnLock}). 판매자를 잠그지 않으면 기다림 자체가 없어 "경쟁이 재현되지 않음"으로
     * 실패한다 — 통과했는데 아무것도 지키지 않는 상태가 되지 않는다.
     */
    @Test
    void 판매자_탈퇴와_겹치면_탈퇴한_판매자의_문의방이_생기지_않는다() throws Exception {
        // Given
        AtomicReference<Throwable> inquiryFailure = new AtomicReference<>();

        // When: 탈퇴가 판매자 계정 행을 잡고 있는 동안 문의가 들어온다
        raceOnLock(
                transactionManager,
                "account",
                () -> {
                    // 탈퇴 경로에서 결과를 가르는 부분만 재현한다 — 판매자 행 잠금과 상태 전이
                    Account seller = accountRepository.findByIdWithLock(sellerId).orElseThrow();
                    seller.withdraw();
                    accountRepository.saveAndFlush(seller);
                },
                () -> chatRoomService.createUsedProductInquiry(buyerId, productId),
                inquiryFailure
        );

        // Then: 비공개 사유는 드러내지 않으므로 탈퇴가 아니라 게시글 없음으로 끝난다
        assertThat(inquiryFailure.get())
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
        assertThat(activeRooms()).isEmpty();
    }

    /**
     * 첫 문의 알림 판정의 경쟁.
     *
     * <p>"문의가 들어왔다" 알림은 방당 한 번이어야 알림 목록에서 의미를 갖는다. 예전에는 알림 처리
     * 쪽에서 메시지 개수를 세어 판정했는데, 그 처리는 AFTER_COMMIT + {@code @Async}라
     * 첫 메시지의 리스너가 돌기 전에 두 번째 메시지가 커밋되면 <b>둘 다 2를 세어</b>
     * 문의 알림이 통째로 사라졌다.
     *
     * <p>지금은 방 행을 잠근 발송 트랜잭션에서 확정해 이벤트에 실어보낸다. 같은 방으로 동시에
     * 들어온 발송은 그 잠금에서 직렬화되므로 정확히 하나만 첫 메시지가 된다.
     */
    @Test
    void 동시에_보낸_두_메시지_중_정확히_하나만_문의_알림이_된다() throws Exception {
        // Given
        Long roomId = chatRoomService.createUsedProductInquiry(buyerId, productId).getRoomId();

        // When: 구매자가 같은 방으로 두 메시지를 동시에 보낸다
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String content = "문의드립니다 " + i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return chatMessageService.sendMessage(buyerId, roomId, textMessage(content));
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Then: 판매자에게 알림 2건이 도착하고 그중 문의 알림은 한 건뿐이다
        List<Notification> sellerNotifications = awaitNotifications(sellerId, 2);
        assertThat(sellerNotifications)
                .filteredOn(n -> n.getType() == NotificationType.USED_PRODUCT_INQUIRY)
                .hasSize(1);
        assertThat(sellerNotifications)
                .filteredOn(n -> n.getType() == NotificationType.CHAT_MESSAGE)
                .hasSize(1);
    }

    /**
     * 알림 요청이 메시지 저장과 <b>같은 트랜잭션</b>에 기록되는지.
     *
     * <p>예전에는 AFTER_COMMIT + @Async가 알림을 만들었다. 비동기 풀이 포화되면 그 작업이
     * 버려져 알림이 아예 생기지 않았고, 메시지 전송은 성공으로 끝나 아무도 알아채지 못했다.
     * 지금은 커밋된 outbox 행이 남으므로 풀 상태와 무관하다.
     */
    @Test
    void 메시지를_보내면_알림_요청이_outbox에_남는다() {
        // Given
        Long roomId = chatRoomService.createUsedProductInquiry(buyerId, productId).getRoomId();
        long before = notificationOutboxRepository.count();

        // When
        chatMessageService.sendMessage(buyerId, roomId, textMessage("문의드립니다"));

        // Then: 스케줄러가 이미 처리했더라도 행 자체는 남아 있어야 한다
        assertThat(notificationOutboxRepository.count())
                .as("알림 요청이 기록되지 않았다 — 비동기 풀이 포화되면 알림이 사라진다")
                .isEqualTo(before + 1);
    }

    @Test
    void outbox_처리는_알림_생성과_상태_전이를_함께_끝낸다() {
        // Given: 둘을 나누면 알림만 만들어지고 DONE을 못 남기는 창이 생기고,
        //        그 행이 다음 주기에 다시 처리돼 같은 알림이 두 번 간다.
        Long roomId = chatRoomService.createUsedProductInquiry(buyerId, productId).getRoomId();
        chatMessageService.sendMessage(buyerId, roomId, textMessage("문의드립니다"));

        // When: 스케줄러(1초 주기)가 처리할 때까지 기다린다
        List<Notification> notifications = awaitNotifications(sellerId, 1);

        // Then
        assertThat(notifications).hasSize(1);
        assertThat(notificationOutboxRepository.findAll())
                .allMatch(outbox -> outbox.getStatus() == OutboxStatus.DONE);
    }

    private ChatMessageSendRequestDto textMessage(String content) {
        ChatMessageSendRequestDto dto = new ChatMessageSendRequestDto();
        ReflectionTestUtils.setField(dto, "content", content);
        return dto;
    }

    // 알림은 AFTER_COMMIT @Async로 만들어진다 — 개수가 찰 때까지 기다린다.
    private List<Notification> awaitNotifications(Long accountId, int expected) {
        for (int attempt = 0; attempt < 100; attempt++) {
            List<Notification> found = notificationRepository.findAll().stream()
                    .filter(n -> n.getAccount().getAccountId().equals(accountId))
                    .toList();
            if (found.size() >= expected) {
                return found;
            }
            sleepQuietly(100);
        }
        throw new AssertionError("알림 " + expected + "건이 도착하지 않았다");
    }

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
