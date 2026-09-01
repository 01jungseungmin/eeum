package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.dto.response.ChatParticipantResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomDetailResponseDto;
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
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.eeum.eeum.common.dto.response.CursorSlice;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 중고거래 1:1 문의방 생성 검증.
 *
 * <p>활성 방 유일성은 MySQL 생성 컬럼({@code active_ref_key})과 그 위의 UNIQUE로 보장한다.
 * 생성식이 {@code ref_type / ref_id / buyer_account_id / is_active}에 의존하고 종료된 방은
 * NULL이 되어 제약에서 빠지므로, 실제 DB 없이는 "활성만 1개, 종료된 방은 누적 가능"을 검증할 수 없다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class UsedProductInquiryRoomIntegrationTest extends IntegrationTestSupport {

    private final ChatRoomService chatRoomService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    private Long sellerId;
    private Long buyerId;
    private Long productId;
    private Long otherProductId;
    private Region region;
    private Category category;
    private Account seller;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);

        seller = accountRepository.save(Account.createUser(
                "seller-" + tag + "@test.com", "pw", "판매자", "판매자" + tag, "010-2222-2222"));
        Account buyer = accountRepository.save(Account.createUser(
                "buyer-" + tag + "@test.com", "pw", "구매자", "구매자" + tag, "010-1111-1111"));
        sellerId = seller.getAccountId();
        buyerId = buyer.getAccountId();

        region = regionRepository.save(
                Region.create("1168" + tag, "서울특별시", "강남구", "역삼동", 3));
        category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기" + tag, 1));

        // 문의 시작은 GPS 인증된 활동 지역을 요구한다.
        verifyRegionFor(buyer);

        productId = saveProduct("자전거 팝니다");
        otherProductId = saveProduct("의자 팝니다");
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
    }

    @Test
    void 문의방을_열면_구매자와_판매자만_참여자로_들어간다() {
        ChatRoomResponseDto created = chatRoomService.createUsedProductInquiry(buyerId, productId);

        ChatRoom room = chatRoomRepository.findById(created.getRoomId()).orElseThrow();
        assertThat(room.getType()).isEqualTo(ChatRoomType.PRIVATE);
        assertThat(room.getRefType()).isEqualTo(ChatRoomRefType.USED_PRODUCT);
        assertThat(room.getRefId()).isEqualTo(productId);
        assertThat(room.getBuyerAccountId()).isEqualTo(buyerId);
        assertThat(chatParticipantRepository
                .countByChatRoom_ChatroomIdAndStatus(room.getChatroomId(), ParticipantStatus.ACTIVE))
                .isEqualTo(2);
    }

    @Test
    void 같은_게시글에_다시_문의하면_기존_방을_돌려준다() {
        // "채팅하기"를 여러 번 눌러도 방이 늘어나지 않아야 한다.
        ChatRoomResponseDto first = chatRoomService.createUsedProductInquiry(buyerId, productId);
        ChatRoomResponseDto second = chatRoomService.createUsedProductInquiry(buyerId, productId);

        assertThat(second.getRoomId()).isEqualTo(first.getRoomId());
        assertThat(activeInquiryCount(productId)).isEqualTo(1);
    }

    @Test
    void 게시글이_다르면_같은_두_사람이라도_방이_따로_생긴다() {
        ChatRoomResponseDto first = chatRoomService.createUsedProductInquiry(buyerId, productId);
        ChatRoomResponseDto second = chatRoomService.createUsedProductInquiry(buyerId, otherProductId);

        assertThat(second.getRoomId()).isNotEqualTo(first.getRoomId());
    }

    @Test
    void 종료된_방이_있어도_같은_조합으로_다시_열_수_있다() {
        // 생성식이 종료된 방을 NULL로 만들고 MySQL UNIQUE는 NULL 중복을 허용한다.
        // 이 성질이 깨지면 한 번 종료한 뒤로는 영영 문의를 못 하게 된다.
        ChatRoomResponseDto first = chatRoomService.createUsedProductInquiry(buyerId, productId);
        ChatRoom room = chatRoomRepository.findById(first.getRoomId()).orElseThrow();
        room.deactivate();
        chatRoomRepository.saveAndFlush(room);

        ChatRoomResponseDto reopened = chatRoomService.createUsedProductInquiry(buyerId, productId);

        assertThat(reopened.getRoomId()).isNotEqualTo(first.getRoomId());
        assertThat(activeInquiryCount(productId)).isEqualTo(1);
    }

    @Test
    void 본인_게시글에는_문의할_수_없다() {
        verifyRegionFor(seller);

        assertThatThrownBy(() -> chatRoomService.createUsedProductInquiry(sellerId, productId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_SELF_INQUIRY_NOT_ALLOWED);
    }

    @Test
    void 삭제된_게시글에는_새_방을_만들_수_없다() {
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        product.softDelete();
        usedProductRepository.saveAndFlush(product);

        assertThatThrownBy(() -> chatRoomService.createUsedProductInquiry(buyerId, productId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    @Test
    void 숨김_게시글에는_새_방을_만들_수_없고_숨김_사실도_드러내지_않는다() {
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        product.hide();
        usedProductRepository.saveAndFlush(product);

        // 삭제와 같은 코드로 응답해 "숨겨진 글이 있다"는 사실이 새어 나가지 않게 한다.
        assertThatThrownBy(() -> chatRoomService.createUsedProductInquiry(buyerId, productId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    @Test
    void 지역_인증이_없으면_문의를_시작할_수_없다() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Account stranger = accountRepository.save(Account.createUser(
                "stranger-" + tag + "@test.com", "pw", "미인증", "미인증" + tag, "010-9999-9999"));

        assertThatThrownBy(() ->
                chatRoomService.createUsedProductInquiry(stranger.getAccountId(), productId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REGION_ACCESS_REQUIRED);

        assertThat(activeInquiryCount(productId)).isZero();
    }

    @Test
    void 같은_상품_같은_구매자의_활성_방은_DB가_두_번째_INSERT를_막는다() {
        // 서비스는 계정 잠금으로 동시 요청을 직렬화하므로, 그 방어가 뚫렸을 때를 가정해
        // 스키마 제약(active_ref_key 생성식 + uk_chat_room_active_ref)만 따로 검증한다.
        // 생성식이 STORE 전용으로 남아 있으면(수동 DDL 누락) 이 테스트가 걸린다.
        Account buyer = accountRepository.findById(buyerId).orElseThrow();
        chatRoomRepository.saveAndFlush(ChatRoom.createPrivateInquiry(buyer, productId));

        assertThatThrownBy(() -> chatRoomRepository
                .saveAndFlush(ChatRoom.createPrivateInquiry(buyer, productId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 종료된_방은_유일성_제약에서_빠진다() {
        // 생성식이 is_active를 보지 않으면 종료된 방까지 키를 점유해 재문의가 영영 막힌다.
        Account buyer = accountRepository.findById(buyerId).orElseThrow();
        ChatRoom closed = chatRoomRepository.saveAndFlush(
                ChatRoom.createPrivateInquiry(buyer, productId));
        closed.deactivate();
        chatRoomRepository.saveAndFlush(closed);

        // 같은 조합으로 다시 INSERT돼야 한다.
        ChatRoom reopened = chatRoomRepository.saveAndFlush(
                ChatRoom.createPrivateInquiry(buyer, productId));

        assertThat(reopened.getChatroomId()).isNotEqualTo(closed.getChatroomId());
    }

    // ===================== 퇴장 정책 =====================
    // PRIVATE 문의방은 둘뿐이라 한 명이 나가면 대화가 성립하지 않는다.
    // 나간 사람만 LEFT로 바꾸면 방은 ACTIVE로 남아, 다시 문의해도 들어갈 수 없는
    // 그 방의 roomId를 돌려받는다 — 재문의가 영영 막힌다.

    @Test
    void 문의방은_한_명만_나가도_방_전체가_종료된다() {
        // Given
        Long roomId = chatRoomService.createUsedProductInquiry(buyerId, productId).getRoomId();

        // When
        chatRoomService.leaveRoom(buyerId, roomId);

        // Then
        assertThat(chatRoomRepository.findById(roomId).orElseThrow().isActive()).isFalse();
    }

    @Test
    void 문의방_퇴장은_참여자를_LEFT로_바꾸지_않는다() {
        // Given: 내 채팅방 목록이 참여자 ACTIVE를 조건으로 걸기 때문에,
        //        LEFT로 바꾸면 나간 쪽은 includeClosed=true로도 지난 대화를 볼 수 없다.
        Long roomId = chatRoomService.createUsedProductInquiry(buyerId, productId).getRoomId();

        // When
        chatRoomService.leaveRoom(buyerId, roomId);

        // Then: 양쪽 모두 ACTIVE로 남는다
        assertThat(chatParticipantRepository.findAllByChatRoom_ChatroomId(roomId))
                .hasSize(2)
                .allMatch(ChatParticipant::isActive);
    }

    @Test
    void 나간_뒤_같은_상품에_다시_문의하면_새_방이_생긴다() {
        // Given
        Long first = chatRoomService.createUsedProductInquiry(buyerId, productId).getRoomId();
        chatRoomService.leaveRoom(buyerId, first);

        // When
        Long second = chatRoomService.createUsedProductInquiry(buyerId, productId).getRoomId();

        // Then
        assertThat(second).isNotEqualTo(first);
        assertThat(activeInquiryCount(productId)).isEqualTo(1);
    }

    @Test
    void 종료된_문의방은_양쪽_모두_지난_대화로_볼_수_있다() {
        // Given
        Long roomId = chatRoomService.createUsedProductInquiry(buyerId, productId).getRoomId();
        chatRoomService.leaveRoom(buyerId, roomId);

        // When & Then: 기본 목록에서는 빠지고, includeClosed=true에서는 양쪽 다 보인다
        for (Long accountId : List.of(buyerId, sellerId)) {
            assertThat(roomIds(accountId, false)).as("기본 목록: " + accountId)
                    .doesNotContain(roomId);
            assertThat(roomIds(accountId, true)).as("지난 대화: " + accountId)
                    .contains(roomId);
        }
    }

    @Test
    void 이미_종료된_문의방에서는_다시_나갈_수_없다() {
        // Given: 참여자를 LEFT로 바꾸지 않으므로 중복 호출을 방 상태로 막아야 한다
        Long roomId = chatRoomService.createUsedProductInquiry(buyerId, productId).getRoomId();
        chatRoomService.leaveRoom(buyerId, roomId);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.leaveRoom(sellerId, roomId))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_INACTIVE);
    }

    // ===================== 표시명 =====================

    @Test
    void 문의방_참여자_응답에_실명이_들어가지_않는다() {
        // Given: Account.name은 실명이다. 중고 문의는 모르는 사람과 연결되는 첫 경로라
        //        여기서 실명이 나가면 게시글에 문의 한 번 거는 것만으로 상대 실명을 알 수 있다.
        Long roomId = chatRoomService.createUsedProductInquiry(buyerId, productId).getRoomId();
        Account sellerAccount = accountRepository.findById(sellerId).orElseThrow();

        // When
        ChatRoomDetailResponseDto detail = chatRoomService.getRoomDetail(buyerId, roomId);

        // Then
        assertThat(detail.getParticipants())
                .extracting(ChatParticipantResponseDto::getName)
                .doesNotContain(sellerAccount.getName())
                .contains(sellerAccount.getNickname());
        assertThat(sellerAccount.getName()).isNotEqualTo(sellerAccount.getNickname());
    }

    @Test
    void 채팅방_목록_응답은_실제_정렬을_그대로_알려준다() {
        // Given: 실제 SQL은 lastMessageAt DESC NULLS LAST, chatroomId DESC로 고정돼 있다.
        //        요청 Pageable을 그대로 돌려주면 sort가 UNSORTED로 나가 실제 순서와 갈린다.
        chatRoomService.createUsedProductInquiry(buyerId, productId);

        // When
        CursorSlice<ChatRoomResponseDto> rooms =
                chatRoomService.getMyRooms(buyerId, null, null, 20, true);

        // Then
        assertThat(rooms.getSort())
                .containsExactly(
                        Sort.Order.desc("lastMessageAt").nullsLast(),
                        Sort.Order.desc("chatroomId"));
    }

    private List<Long> roomIds(Long accountId, boolean includeClosed) {
        return chatRoomService.getMyRooms(accountId, null, null, 20, includeClosed)
                .getContent().stream()
                .map(ChatRoomResponseDto::getRoomId)
                .toList();
    }

    private long activeInquiryCount(Long usedProductId) {
        return chatRoomRepository.findAll().stream()
                .filter(room -> room.isUsedProductRoom()
                        && room.getRefId().equals(usedProductId)
                        && room.isActive())
                .count();
    }

    private void verifyRegionFor(Account account) {
        accountRegionRepository.save(AccountRegion.builder()
                .account(account)
                .region(region)
                .verified(true)
                .verifiedAt(LocalDateTime.now())
                .build());
    }

    private Long saveProduct(String title) {
        return usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region, title, "설명",
                UsedProductPriceType.FIXED, new BigDecimal("10000"))).getUsedProductId();
    }
}
