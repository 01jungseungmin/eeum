package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiDraftCapacityRecorderTest {

    @InjectMocks
    private AiDraftCapacityRecorder draftCapacityRecorder;

    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;

    private static final Long STORE_ID = 1L;

    private Store createStore() {
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(STORE_ID);
        return store;
    }

    // ──────────────────── validate (퇴거 없음) ────────────────────

    @Test
    void 타입별_DRAFT가_캡_미만이면_초안_보관_캡_검증을_통과한다() {
        // given
        Store store = createStore();
        when(aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatus(
                STORE_ID, AiMessageType.COMPLAINT_REPLY, AiMessageStatus.DRAFT))
                .thenReturn(19L);

        // when & then
        draftCapacityRecorder.validate(store, AiMessageType.COMPLAINT_REPLY, false);
        verify(aiGeneratedMessageRepository, never()).delete(any());
    }

    @Test
    void 캡_초과_confirmDelete_false면_AI_DRAFT_LIMIT_EXCEEDED_예외와_상세정보가_함께_던져진다() {
        // given
        Store store = createStore();
        when(aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatus(
                STORE_ID, AiMessageType.COMPLAINT_REPLY, AiMessageStatus.DRAFT))
                .thenReturn(20L);

        // when & then
        assertThatThrownBy(() -> draftCapacityRecorder.validate(store, AiMessageType.COMPLAINT_REPLY, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_DRAFT_LIMIT_EXCEEDED);
        verify(aiGeneratedMessageRepository, never()).delete(any());
    }

    @Test
    void 캡_초과_confirmDelete_true면_예외_없이_통과하고_퇴거는_하지_않는다() {
        // given — 실제 퇴거는 새 초안 저장 성공 후 evictExcessInTx가 처리한다 (데이터 손실 방지)
        Store store = createStore();
        when(aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatus(
                STORE_ID, AiMessageType.COMPLAINT_REPLY, AiMessageStatus.DRAFT))
                .thenReturn(20L);

        // when & then
        draftCapacityRecorder.validate(store, AiMessageType.COMPLAINT_REPLY, true);
        verify(aiGeneratedMessageRepository, never())
                .findFirstByStore_StoreIdAndTypeAndStatusOrderByCreatedAtAsc(any(), any(), any());
        verify(aiGeneratedMessageRepository, never()).delete(any());
    }

    // ──────────────────── evictExcessInTx (저장 성공 후 자기치유) ────────────────────

    @Test
    void 저장_후_캡_이내면_아무것도_퇴거하지_않는다() {
        // given
        Store store = createStore();
        when(aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatus(
                STORE_ID, AiMessageType.COMPLAINT_REPLY, AiMessageStatus.DRAFT))
                .thenReturn(20L);

        // when
        draftCapacityRecorder.evictExcessInTx(store, AiMessageType.COMPLAINT_REPLY);

        // then
        verify(aiGeneratedMessageRepository, never()).delete(any());
    }

    @Test
    void 저장_후_캡을_1건_초과하면_가장_오래된_DRAFT_1건만_퇴거된다() {
        // given
        Store store = createStore();
        Account ownerAccount = mock(Account.class);
        AiGeneratedMessage oldestDraft = AiGeneratedMessage.createDraft(
                store, ownerAccount, AiMessageType.COMPLAINT_REPLY,
                "COMPLAINT_KEYWORD", null, "제목", "내용", AiChannel.APP_PUSH);
        when(aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatus(
                STORE_ID, AiMessageType.COMPLAINT_REPLY, AiMessageStatus.DRAFT))
                .thenReturn(21L);
        when(aiGeneratedMessageRepository.findFirstByStore_StoreIdAndTypeAndStatusOrderByCreatedAtAsc(
                STORE_ID, AiMessageType.COMPLAINT_REPLY, AiMessageStatus.DRAFT))
                .thenReturn(Optional.of(oldestDraft));

        // when
        draftCapacityRecorder.evictExcessInTx(store, AiMessageType.COMPLAINT_REPLY);

        // then
        verify(aiGeneratedMessageRepository, times(1)).delete(oldestDraft);
    }

    @Test
    void 퇴거할_초과분이_이미_없어졌으면_삭제_호출_없이_넘어간다() {
        // given — 초과분은 있지만(count=21) 동시 요청으로 이미 다른 스레드가 퇴거를 마쳐 대상이 없는 방어적 케이스
        Store store = createStore();
        when(aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatus(
                STORE_ID, AiMessageType.COMPLAINT_REPLY, AiMessageStatus.DRAFT))
                .thenReturn(21L);
        when(aiGeneratedMessageRepository.findFirstByStore_StoreIdAndTypeAndStatusOrderByCreatedAtAsc(
                STORE_ID, AiMessageType.COMPLAINT_REPLY, AiMessageStatus.DRAFT))
                .thenReturn(Optional.empty());

        // when & then
        draftCapacityRecorder.evictExcessInTx(store, AiMessageType.COMPLAINT_REPLY);
        verify(aiGeneratedMessageRepository, never()).delete(any());
    }
}
