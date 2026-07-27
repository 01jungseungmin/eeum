package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiExposureStatusResponseDto;
import com.eeum.eeum.application.ai.generator.TemplateAiInsightGenerator;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiExposureStatus;
import com.eeum.eeum.domain.ai.repository.AiExposureStatusRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiLocalMatchServiceTest {

    @InjectMocks
    private AiLocalMatchService localMatchService;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiExposureStatusRepository aiExposureStatusRepository;
    @Mock private AiExposureCommandExecutor exposureCommandExecutor;
    @Mock private OrderRepository orderRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private RedisLockService redisLockService;
    @Spy private TemplateAiInsightGenerator aiInsightGenerator = new TemplateAiInsightGenerator();

    private static final Long OWNER_ID = 100L;
    private static final Long STORE_ID = 1L;

    // ──────────────────── Helpers ────────────────────

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(STORE_ID);
        lenient().when(store.getName()).thenReturn("테스트 상점");
        lenient().when(store.getAccount()).thenReturn(mock(Account.class));
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    @SuppressWarnings("unchecked")
    private void stubLockPassThrough() {
        when(redisLockService.executeWithLock(anyString(), any(Duration.class), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(2)).get());
    }

    private void stubEmptyCustomers() {
        lenient().when(orderRepository.findOrderAccountIdsByStoreIdAndStatus(anyLong(), any()))
                .thenReturn(List.of());
        lenient().when(favoriteRepository.findAccountIdsByRefTypeAndRefId(any(), anyLong()))
                .thenReturn(List.of());
        lenient().when(orderRepository.findRegularAccountIds(anyLong(), any(), anyLong()))
                .thenReturn(List.of());
    }

    // ──────────────────── Tests ────────────────────

    @Test
    void 노출_시작에_성공하면_active_상태와_시작_시각이_기록된다() {
        // given
        Store store = stubStore();
        stubLockPassThrough();
        AiExposureStatus status = AiExposureStatus.init(store);
        status.start(LocalDateTime.now(), 0);
        when(exposureCommandExecutor.startExposureInTx(store, OWNER_ID)).thenReturn(status);

        // when
        AiExposureStatusResponseDto response = localMatchService.startExposure(OWNER_ID);

        // then
        assertThat(response.isActive()).isTrue();
        assertThat(response.getStartedAt()).isNotNull();
        verify(exposureCommandExecutor).startExposureInTx(store, OWNER_ID);
    }

    @Test
    void 이미_노출_진행_중일_때_다시_시작하면_AI_INVALID_STATUS_예외가_발생한다() {
        // given
        Store store = stubStore();
        stubLockPassThrough();
        when(exposureCommandExecutor.startExposureInTx(store, OWNER_ID))
                .thenThrow(new BusinessException(ErrorCode.AI_INVALID_STATUS));

        // when & then
        assertThatThrownBy(() -> localMatchService.startExposure(OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_INVALID_STATUS);
    }

    @Test
    void 노출_중지에_성공하면_active가_false가_되고_중지_시각이_기록된다() {
        // given
        Store store = stubStore();
        stubLockPassThrough();
        AiExposureStatus status = AiExposureStatus.init(store);
        status.start(LocalDateTime.now(), 5);
        status.stop(LocalDateTime.now());
        when(exposureCommandExecutor.stopExposureInTx(store, OWNER_ID)).thenReturn(status);

        // when
        AiExposureStatusResponseDto response = localMatchService.stopExposure(OWNER_ID);

        // then
        assertThat(response.isActive()).isFalse();
        assertThat(response.getStoppedAt()).isNotNull();
        verify(exposureCommandExecutor).stopExposureInTx(store, OWNER_ID);
    }

    @Test
    void 노출_진행_중이_아닐_때_중지하면_AI_INVALID_STATUS_예외가_발생한다() {
        // given
        Store store = stubStore();
        stubLockPassThrough();
        when(exposureCommandExecutor.stopExposureInTx(store, OWNER_ID))
                .thenThrow(new BusinessException(ErrorCode.AI_INVALID_STATUS));

        // when & then
        assertThatThrownBy(() -> localMatchService.stopExposure(OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_INVALID_STATUS);
    }

    @Test
    void 고객_데이터가_없으면_hasData_false와_빈_세그먼트로_응답한다() {
        // given
        stubStore();
        stubEmptyCustomers();
        when(chatParticipantRepository.findActiveParticipantAccountIdsByStoreRefId(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(aiExposureStatusRepository.findByStore_StoreId(STORE_ID)).thenReturn(Optional.empty());

        // when
        var response = localMatchService.getLocalMatch(OWNER_ID);

        // then
        assertThat(response.isHasData()).isFalse();
        assertThat(response.getSegments()).isEmpty();
        assertThat(response.getEstimatedTargetCount()).isZero();
    }
}
