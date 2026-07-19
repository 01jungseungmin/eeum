package com.eeum.eeum.application.store.service;

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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @InjectMocks
    private StoreService storeService;

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

    // ──────────────────── 대시보드 — 채팅방 개설 여부 ────────────────────

    @Test
    void 상점_채팅방이_개설되어_있으면_대시보드에_채팅방_ID가_포함된다() {
        // given
        Store store = createStore();
        when(storeRepository.findByAccount_AccountId(OWNER_ACCOUNT_ID)).thenReturn(Optional.of(store));
        when(chatRoomRepository.findAllByRefTypeAndRefId(ChatRoomRefType.STORE, STORE_ID))
                .thenReturn(List.of(createStoreChatRoom()));

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
        when(chatRoomRepository.findAllByRefTypeAndRefId(ChatRoomRefType.STORE, STORE_ID))
                .thenReturn(List.of());

        // when
        StoreDashboardResponseDto dashboard = storeService.getDashboard(OWNER_ACCOUNT_ID);

        // then
        assertThat(dashboard.isStoreChatRoomCreated()).isFalse();
        assertThat(dashboard.getStoreChatRoomId()).isNull();
    }

    @Test
    void GROUP과_GROUP_STREET_방이_공존하면_GROUP_방을_우선_반환한다() {
        // given
        Store store = createStore();
        ChatRoom streetRoom = ChatRoom.createGroup(
                mock(Account.class), ChatRoomType.GROUP_STREET, "동네방",
                ChatRoomRefType.STORE, STORE_ID, null);
        ReflectionTestUtils.setField(streetRoom, "chatroomId", 99L);
        when(storeRepository.findByAccount_AccountId(OWNER_ACCOUNT_ID)).thenReturn(Optional.of(store));
        when(chatRoomRepository.findAllByRefTypeAndRefId(ChatRoomRefType.STORE, STORE_ID))
                .thenReturn(List.of(streetRoom, createStoreChatRoom()));

        // when
        StoreDashboardResponseDto dashboard = storeService.getDashboard(OWNER_ACCOUNT_ID);

        // then: 단건 Optional이었다면 2건 조회로 예외가 났을 상황 — GROUP 방 ID가 선택된다
        assertThat(dashboard.isStoreChatRoomCreated()).isTrue();
        assertThat(dashboard.getStoreChatRoomId()).isEqualTo(CHAT_ROOM_ID);
    }

    @Test
    void 비활성화된_상점_채팅방은_개설되지_않은_것으로_처리된다() {
        // given
        Store store = createStore();
        ChatRoom deactivatedRoom = createStoreChatRoom();
        deactivatedRoom.deactivate();
        when(storeRepository.findByAccount_AccountId(OWNER_ACCOUNT_ID)).thenReturn(Optional.of(store));
        when(chatRoomRepository.findAllByRefTypeAndRefId(ChatRoomRefType.STORE, STORE_ID))
                .thenReturn(List.of(deactivatedRoom));

        // when
        StoreDashboardResponseDto dashboard = storeService.getDashboard(OWNER_ACCOUNT_ID);

        // then
        assertThat(dashboard.isStoreChatRoomCreated()).isFalse();
        assertThat(dashboard.getStoreChatRoomId()).isNull();
    }
}
