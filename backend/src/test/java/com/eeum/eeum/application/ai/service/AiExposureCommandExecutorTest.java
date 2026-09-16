package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiLocalMatchConditionRequestDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.entity.AiExposureStatus;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiCustomerType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiExposureStatusRepository;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiExposureCommandExecutorTest {

    @InjectMocks
    private AiExposureCommandExecutor executor;

    @Mock private AiExposureStatusRepository aiExposureStatusRepository;
    @Mock private AiActionLogRepository aiActionLogRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private AccountRepository accountRepository;

    private static final Long STORE_ID = 1L;
    private static final Long OWNER_ID = 100L;

    // ──────────────────── Helpers ────────────────────

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(STORE_ID);
        return store;
    }

    private void stubCustomerSources(List<Long> orderIds, List<Long> favoriteIds, List<Long> chatIds) {
        when(orderRepository.findOrderAccountIdsByStoreIdAndStatus(eq(STORE_ID), eq(OrderStatus.COMPLETED)))
                .thenReturn(orderIds);
        when(favoriteRepository.findAccountIdsByRefTypeAndRefId(eq(FavoriteRefType.STORE), eq(STORE_ID)))
                .thenReturn(favoriteIds);
        when(chatParticipantRepository.findActiveParticipantAccountIdsByStoreRefId(
                eq(STORE_ID), eq(ChatRoomRefType.STORE), eq(ParticipantStatus.ACTIVE)))
                .thenReturn(chatIds);
    }

    private AiExposureStatus activeStatus(Store store) {
        AiExposureStatus status = AiExposureStatus.init(store);
        status.start(LocalDateTime.now(), 0);
        return status;
    }

    // ──────────────────── startExposureInTx ────────────────────

    @Test
    void 노출_시작_시_상태가_없으면_초기화_후_활성화되고_전체_고객수가_계산된다() {
        // given
        Store store = stubStore();
        when(aiExposureStatusRepository.findByStore_StoreId(STORE_ID)).thenReturn(Optional.empty());
        when(aiExposureStatusRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubCustomerSources(List.of(1L, 2L), List.of(2L, 3L), List.of(3L, 4L));
        when(accountRepository.getReferenceById(OWNER_ID)).thenReturn(mock(Account.class));

        // when
        AiExposureStatus result = executor.startExposureInTx(store, OWNER_ID);

        // then — 주문 {1,2} ∪ 찜 {2,3} ∪ 채팅 {3,4} = 4명
        assertThat(result.isActive()).isTrue();
        assertThat(result.getTargetCount()).isEqualTo(4);
        assertThat(result.getStartedAt()).isNotNull();

        ArgumentCaptor<AiActionLog> logCaptor = ArgumentCaptor.forClass(AiActionLog.class);
        verify(aiActionLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getActionType()).isEqualTo(AiActionType.EXPOSURE_STARTED);
        verify(accountRepository).getReferenceById(eq(OWNER_ID));
    }

    @Test
    void 이미_노출중인_상태에서_시작하면_AI_INVALID_STATUS_예외가_발생한다() {
        // given
        Store store = stubStore();
        when(aiExposureStatusRepository.findByStore_StoreId(STORE_ID))
                .thenReturn(Optional.of(activeStatus(store)));
        stubCustomerSources(List.of(), List.of(), List.of());

        // when & then
        assertThatThrownBy(() -> executor.startExposureInTx(store, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_INVALID_STATUS);
        verify(aiActionLogRepository, never()).save(any());
    }

    // ──────────────────── stopExposureInTx ────────────────────

    @Test
    void 노출_중지_시_비활성화되고_중지_액션로그가_저장된다() {
        // given
        Store store = stubStore();
        AiExposureStatus status = activeStatus(store);
        when(aiExposureStatusRepository.findByStore_StoreId(STORE_ID)).thenReturn(Optional.of(status));
        when(accountRepository.getReferenceById(OWNER_ID)).thenReturn(mock(Account.class));

        // when
        AiExposureStatus result = executor.stopExposureInTx(store, OWNER_ID);

        // then
        assertThat(result.isActive()).isFalse();
        assertThat(result.getStoppedAt()).isNotNull();

        ArgumentCaptor<AiActionLog> logCaptor = ArgumentCaptor.forClass(AiActionLog.class);
        verify(aiActionLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getActionType()).isEqualTo(AiActionType.EXPOSURE_STOPPED);
    }

    @Test
    void 노출_상태가_없으면_중지_시_AI_EXPOSURE_NOT_FOUND_예외가_발생한다() {
        // given
        Store store = stubStore();
        when(aiExposureStatusRepository.findByStore_StoreId(STORE_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> executor.stopExposureInTx(store, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_EXPOSURE_NOT_FOUND);
        verify(aiActionLogRepository, never()).save(any());
    }

    @Test
    void 이미_중지된_상태에서_중지하면_AI_INVALID_STATUS_예외가_발생한다() {
        // given
        Store store = stubStore();
        when(aiExposureStatusRepository.findByStore_StoreId(STORE_ID))
                .thenReturn(Optional.of(AiExposureStatus.init(store)));

        // when & then
        assertThatThrownBy(() -> executor.stopExposureInTx(store, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_INVALID_STATUS);
    }

    // ──────────────────── updateConditionsInTx ────────────────────

    @Test
    void 조건_변경_시_고객유형이_null이면_기존_유형_기준으로_대상을_계산한다() {
        // given — 기존 상태의 기본 유형은 ALL
        Store store = stubStore();
        when(aiExposureStatusRepository.findByStore_StoreId(STORE_ID))
                .thenReturn(Optional.of(AiExposureStatus.init(store)));
        stubCustomerSources(List.of(1L), List.of(2L), List.of(3L));
        AiLocalMatchConditionRequestDto request =
                new AiLocalMatchConditionRequestDto(3.0, "한식", null);

        // when
        AiExposureStatus result = executor.updateConditionsInTx(store, request);

        // then
        assertThat(result.getRadiusKm()).isEqualTo(3.0);
        assertThat(result.getInterest()).isEqualTo("한식");
        assertThat(result.getCustomerType()).isEqualTo(AiCustomerType.ALL);
        assertThat(result.getTargetCount()).isEqualTo(3);
    }

    @Test
    void 고객유형이_NEW면_전체_고객에서_주문_고객을_뺀_수가_대상이_된다() {
        // given — 전체 {1,2,3}, 주문 {1,2} → NEW 1명
        Store store = stubStore();
        when(aiExposureStatusRepository.findByStore_StoreId(STORE_ID))
                .thenReturn(Optional.of(AiExposureStatus.init(store)));
        stubCustomerSources(List.of(1L, 2L), List.of(2L, 3L), List.of());
        AiLocalMatchConditionRequestDto request =
                new AiLocalMatchConditionRequestDto(null, null, AiCustomerType.NEW);

        // when
        AiExposureStatus result = executor.updateConditionsInTx(store, request);

        // then
        assertThat(result.getCustomerType()).isEqualTo(AiCustomerType.NEW);
        assertThat(result.getTargetCount()).isEqualTo(1);
    }

    @Test
    void 고객유형이_REGULAR면_주문_3회_이상_단골_수가_대상이_된다() {
        // given
        Store store = stubStore();
        when(aiExposureStatusRepository.findByStore_StoreId(STORE_ID))
                .thenReturn(Optional.of(AiExposureStatus.init(store)));
        stubCustomerSources(List.of(1L), List.of(), List.of());
        when(orderRepository.countRegularAccounts(eq(STORE_ID), eq(OrderStatus.COMPLETED), eq(3L)))
                .thenReturn(7L);
        AiLocalMatchConditionRequestDto request =
                new AiLocalMatchConditionRequestDto(null, null, AiCustomerType.REGULAR);

        // when
        AiExposureStatus result = executor.updateConditionsInTx(store, request);

        // then
        assertThat(result.getTargetCount()).isEqualTo(7);
        verify(orderRepository).countRegularAccounts(eq(STORE_ID), eq(OrderStatus.COMPLETED), eq(3L));
    }

    @Test
    void 조건_변경_시_상태가_없으면_초기화해서_저장한다() {
        // given
        Store store = stubStore();
        when(aiExposureStatusRepository.findByStore_StoreId(STORE_ID)).thenReturn(Optional.empty());
        when(aiExposureStatusRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubCustomerSources(List.of(), List.of(), List.of());
        AiLocalMatchConditionRequestDto request =
                new AiLocalMatchConditionRequestDto(1.0, null, null);

        // when
        AiExposureStatus result = executor.updateConditionsInTx(store, request);

        // then
        assertThat(result.isActive()).isFalse();
        assertThat(result.getRadiusKm()).isEqualTo(1.0);
        verify(aiExposureStatusRepository).save(any(AiExposureStatus.class));
    }
}
