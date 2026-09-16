package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.store.dto.request.StoreStatusUpdateRequestDto;
import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.store.dto.response.StoreDashboardResponseDto;
import com.eeum.eeum.application.store.mapper.StoreMapper;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreImageRepository;
import com.eeum.eeum.domain.store.repository.StoreNoticeRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @InjectMocks
    private StoreService storeService;

    @Mock private FileStorageService fileStorageService;
    @Mock private StoreRepository storeRepository;
    @Mock private StoreNoticeRepository storeNoticeRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private StoreBusinessHourRepository storeBusinessHourRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StoreImageRepository storeImageRepository;
    @Mock private VisitReservationRepository visitReservationRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private StoreMapper storeMapper;

    private static final Long OWNER_ACCOUNT_ID = 100L;
    private static final Long STORE_ID = 1L;
    private static final Long CHAT_ROOM_ID = 15L;

    // ──────────────────── Helpers ────────────────────

    private Store createStore() {
        Store store = Store.createForOwnerSignup(mock(Account.class), "테스트 상점", "서울시", "010-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", STORE_ID);
        return store;
    }

    private ChatRoom createStoreChatRoom() {
        ChatRoom room = ChatRoom.createGroup(
                mock(Account.class), ChatRoomType.GROUP, "상점 단톡방",
                ChatRoomRefType.STORE, STORE_ID, null);
        ReflectionTestUtils.setField(room, "chatroomId", CHAT_ROOM_ID);
        return room;
    }

    @Test
    void 사장은_관리자에게_정지된_상점을_OPEN으로_변경할_수_없다() {
        Store store = createStore();
        store.suspend();
        StoreStatusUpdateRequestDto request = new StoreStatusUpdateRequestDto();
        ReflectionTestUtils.setField(request, "status", StoreStatus.OPEN);
        when(storeRepository.findByAccountIdWithPessimisticLock(OWNER_ACCOUNT_ID))
                .thenReturn(Optional.of(store));

        assertThatThrownBy(() -> storeService.updateStoreStatus(OWNER_ACCOUNT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_SUSPENDED);
        assertThat(store.getStatus()).isEqualTo(StoreStatus.SUSPENDED);
    }

    @Test
    void 사장_상태변경은_계정의_상점_행을_쓰기잠금으로_조회한다() {
        Store store = createStore();
        StoreStatusUpdateRequestDto request = new StoreStatusUpdateRequestDto();
        ReflectionTestUtils.setField(request, "status", StoreStatus.OPEN);
        when(storeRepository.findByAccountIdWithPessimisticLock(OWNER_ACCOUNT_ID))
                .thenReturn(Optional.of(store));

        storeService.updateStoreStatus(OWNER_ACCOUNT_ID, request);

        assertThat(store.getStatus()).isEqualTo(StoreStatus.OPEN);
        org.mockito.Mockito.verify(storeRepository)
                .findByAccountIdWithPessimisticLock(OWNER_ACCOUNT_ID);
    }

    // ──────────────────── 대시보드 — 채팅방 개설 여부 ────────────────────

    @Test
    void 상점_채팅방이_개설되어_있으면_대시보드에_채팅방_ID가_포함된다() {
        // given
        Store store = createStore();
        when(storeRepository.findByAccount_AccountId(OWNER_ACCOUNT_ID)).thenReturn(Optional.of(store));
        when(chatRoomRepository.findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
                ChatRoomRefType.STORE, STORE_ID))
                .thenReturn(Optional.of(createStoreChatRoom()));

        // when
        StoreDashboardResponseDto dashboard = storeService.getDashboard(OWNER_ACCOUNT_ID);

        // then
        assertThat(dashboard.isStoreChatRoomCreated()).isTrue();
        assertThat(dashboard.getStoreChatRoomId()).isEqualTo(CHAT_ROOM_ID);
    }

    @Test
    void 상점_채팅방이_없으면_개설여부는_false이고_ID는_null이다() {
        // given
        Store store = createStore();
        when(storeRepository.findByAccount_AccountId(OWNER_ACCOUNT_ID)).thenReturn(Optional.of(store));
        when(chatRoomRepository.findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
                ChatRoomRefType.STORE, STORE_ID))
                .thenReturn(Optional.empty());

        // when
        StoreDashboardResponseDto dashboard = storeService.getDashboard(OWNER_ACCOUNT_ID);

        // then
        assertThat(dashboard.isStoreChatRoomCreated()).isFalse();
        assertThat(dashboard.getStoreChatRoomId()).isNull();
    }

    @Test
    void 종료된_방만_남아있으면_개설되지_않은_것으로_처리된다() {
        // given: 조회는 ACTIVE 방만 대상으로 하므로 전부 종료된 상점은 빈 결과가 된다
        Store store = createStore();
        when(storeRepository.findByAccount_AccountId(OWNER_ACCOUNT_ID)).thenReturn(Optional.of(store));
        when(chatRoomRepository.findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
                ChatRoomRefType.STORE, STORE_ID))
                .thenReturn(Optional.empty());

        // when
        StoreDashboardResponseDto dashboard = storeService.getDashboard(OWNER_ACCOUNT_ID);

        // then
        assertThat(dashboard.isStoreChatRoomCreated()).isFalse();
        assertThat(dashboard.getStoreChatRoomId()).isNull();
    }

    @Test
    void 재생성된_상점_채팅방은_종료된_옛_방이_아니라_최신_ACTIVE_방을_반환한다() {
        // given: 종료 후 재생성으로 (STORE, storeId) 조합의 방이 여러 건 누적된 상황.
        //        정렬 없는 단건 Optional 조회였다면 예외가 났을 케이스다.
        Store store = createStore();
        ChatRoom recreatedRoom = ChatRoom.createGroup(
                mock(Account.class), ChatRoomType.GROUP, "테스트 상점 단톡방",
                ChatRoomRefType.STORE, STORE_ID, null);
        ReflectionTestUtils.setField(recreatedRoom, "chatroomId", 99L);
        when(storeRepository.findByAccount_AccountId(OWNER_ACCOUNT_ID)).thenReturn(Optional.of(store));
        when(chatRoomRepository.findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
                ChatRoomRefType.STORE, STORE_ID))
                .thenReturn(Optional.of(recreatedRoom));

        // when
        StoreDashboardResponseDto dashboard = storeService.getDashboard(OWNER_ACCOUNT_ID);

        // then
        assertThat(dashboard.isStoreChatRoomCreated()).isTrue();
        assertThat(dashboard.getStoreChatRoomId()).isEqualTo(99L);
    }
}
