package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.application.ai.policy.AiPlanPolicy;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.enums.AiUsageType;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import com.eeum.eeum.domain.ai.repository.AiUsageLogRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiManagerSupportServiceTest {

    @InjectMocks
    private AiManagerSupportService supportService;

    @Mock private StoreRepository storeRepository;
    @Mock private AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    @Mock private AiUsageLogRepository aiUsageLogRepository;
    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private RedisLockService redisLockService;
    @Mock private AiUsageRecorder aiUsageRecorder;
    @Spy private AiPlanPolicy aiPlanPolicy = new AiPlanPolicy();

    private static final Long STORE_ID = 1L;
    private static final Long OWNER_ID = 100L;

    // ──────────────────── Helpers ────────────────────

    private Store createStore() {
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(STORE_ID);
        return store;
    }

    private void stubPlan(AiPlanType planType) {
        Store store = mock(Store.class);
        AiPlanSubscription subscription = AiPlanSubscription.create(store, planType, LocalDateTime.now());
        when(aiPlanSubscriptionRepository.findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(Optional.of(subscription));
    }

    private void stubLockPassThrough() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return null;
        }).when(redisLockService).executeWithLock(anyString(), any(Duration.class), any(Runnable.class));
    }

    // ──────────────────── Tests ────────────────────

    @Test
    void Free_플랜이_생성_기능_호출_시_AI_PLAN_REQUIRED_예외가_발생한다() {
        // given
        Store store = createStore();
        stubPlan(AiPlanType.FREE);

        // when & then
        assertThatThrownBy(() -> supportService.consumeGeneration(
                store, OWNER_ID, AiFeature.CUSTOMER_CARE_DRAFT, AiUsageType.CUSTOMER_CARE_DRAFT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_PLAN_REQUIRED);
    }

    @Test
    void Basic_플랜_월_한도_초과_시_AiUsageRecorder가_던진_예외가_전파된다() {
        // given
        Store store = createStore();
        stubPlan(AiPlanType.BASIC);
        stubLockPassThrough();
        doThrow(new BusinessException(ErrorCode.AI_USAGE_LIMIT_EXCEEDED))
                .when(aiUsageRecorder).checkAndRecord(any(), anyLong(), any(), any(), anyString());

        // when & then
        assertThatThrownBy(() -> supportService.consumeGeneration(
                store, OWNER_ID, AiFeature.MARKETING_DRAFT, AiUsageType.MARKETING_DRAFT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_USAGE_LIMIT_EXCEEDED);
    }

    @Test
    void 생성_기능_호출_시_락_안에서_AiUsageRecorder가_호출된다() {
        // given
        Store store = createStore();
        stubPlan(AiPlanType.BASIC);
        stubLockPassThrough();

        // when
        supportService.consumeGeneration(store, OWNER_ID, AiFeature.MARKETING_DRAFT, AiUsageType.MARKETING_DRAFT);

        // then
        verify(aiUsageRecorder).checkAndRecord(any(), anyLong(), any(), any(), anyString());
    }

    @Test
    void Pro_플랜도_AiUsageRecorder를_통해_사용량이_기록된다() {
        // given
        Store store = createStore();
        stubPlan(AiPlanType.PRO);
        stubLockPassThrough();

        // when
        supportService.consumeGeneration(store, OWNER_ID, AiFeature.MARKETING_DRAFT, AiUsageType.MARKETING_DRAFT);

        // then
        verify(aiUsageRecorder).checkAndRecord(any(), anyLong(), any(), any(), anyString());
    }

    @Test
    void 구독_정보가_없으면_기본_플랜은_FREE다() {
        // given
        when(aiPlanSubscriptionRepository.findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(Optional.empty());

        // when
        AiPlanType planType = supportService.getPlanType(STORE_ID);

        // then
        assertThat(planType).isEqualTo(AiPlanType.FREE);
    }

    @Test
    void 구독_미존재_사장이_생성_기능_호출_시_AI_PLAN_REQUIRED_예외가_발생한다() {
        // given
        Store store = createStore();
        when(aiPlanSubscriptionRepository.findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> supportService.consumeGeneration(
                store, OWNER_ID, AiFeature.CUSTOMER_CARE_DRAFT, AiUsageType.CUSTOMER_CARE_DRAFT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_PLAN_REQUIRED);
    }

    @Test
    void 다른_사장의_메시지_접근_시_AI_FORBIDDEN_예외가_발생한다() {
        // given
        Store store = mock(Store.class);
        Account otherOwner = mock(Account.class);
        when(otherOwner.getAccountId()).thenReturn(999L);
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, otherOwner, AiMessageType.CUSTOMER_CARE, null, null, "제목", "내용", AiChannel.APP_PUSH);
        when(aiGeneratedMessageRepository.findById(10L)).thenReturn(Optional.of(message));

        // when & then
        assertThatThrownBy(() -> supportService.getOwnedMessage(OWNER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_FORBIDDEN);
    }

    @Test
    void 존재하지_않는_메시지_접근_시_AI_MESSAGE_NOT_FOUND_예외가_발생한다() {
        // given
        when(aiGeneratedMessageRepository.findById(10L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> supportService.getOwnedMessage(OWNER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_MESSAGE_NOT_FOUND);
    }

    @Test
    void 타입별_DRAFT가_캡_미만이면_초안_보관_캡_검증을_통과한다() {
        // given
        Store store = createStore();
        when(aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatus(
                STORE_ID, AiMessageType.COMPLAINT_REPLY, com.eeum.eeum.domain.ai.enums.AiMessageStatus.DRAFT))
                .thenReturn(19L);

        // when & then
        supportService.enforceDraftCapacity(store, AiMessageType.COMPLAINT_REPLY, false);
        verify(aiGeneratedMessageRepository, org.mockito.Mockito.never())
                .findFirstByStore_StoreIdAndTypeAndStatusOrderByCreatedAtAsc(any(), any(), any());
    }

    @Test
    void 캡_초과_confirmDelete_false면_AI_DRAFT_LIMIT_EXCEEDED_예외와_상세정보가_함께_던져진다() {
        // given
        Store store = createStore();
        when(aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatus(
                STORE_ID, AiMessageType.COMPLAINT_REPLY, com.eeum.eeum.domain.ai.enums.AiMessageStatus.DRAFT))
                .thenReturn(20L);

        // when & then
        assertThatThrownBy(() -> supportService.enforceDraftCapacity(store, AiMessageType.COMPLAINT_REPLY, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_DRAFT_LIMIT_EXCEEDED);
    }

    @Test
    void 캡_초과_confirmDelete_true면_가장_오래된_DRAFT가_삭제되고_예외는_발생하지_않는다() {
        // given
        Store store = createStore();
        Account ownerAccount = mock(Account.class);
        AiGeneratedMessage oldestDraft = AiGeneratedMessage.createDraft(
                store, ownerAccount, AiMessageType.COMPLAINT_REPLY,
                "COMPLAINT_KEYWORD", null, "제목", "내용", AiChannel.APP_PUSH);
        when(aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatus(
                STORE_ID, AiMessageType.COMPLAINT_REPLY, com.eeum.eeum.domain.ai.enums.AiMessageStatus.DRAFT))
                .thenReturn(20L);
        when(aiGeneratedMessageRepository.findFirstByStore_StoreIdAndTypeAndStatusOrderByCreatedAtAsc(
                STORE_ID, AiMessageType.COMPLAINT_REPLY, com.eeum.eeum.domain.ai.enums.AiMessageStatus.DRAFT))
                .thenReturn(Optional.of(oldestDraft));

        // when
        supportService.enforceDraftCapacity(store, AiMessageType.COMPLAINT_REPLY, true);

        // then
        verify(aiGeneratedMessageRepository).delete(oldestDraft);
    }
}
