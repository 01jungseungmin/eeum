package com.eeum.eeum.application.inquiry.service;

import com.eeum.eeum.application.inquiry.dto.request.InquiryAnswerCreateRequestDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryAnswerResponseDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryDetailResponseDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.entity.InquiryAnswer;
import com.eeum.eeum.domain.inquiry.enums.InquiryAnswerWriterType;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.event.InquiryAnsweredEvent;
import com.eeum.eeum.domain.inquiry.repository.InquiryAnswerRepository;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerInquiryServiceTest {

    @InjectMocks private OwnerInquiryService ownerInquiryService;

    @Mock private InquiryRepository inquiryRepository;
    @Mock private InquiryAnswerRepository inquiryAnswerRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private InquiryService inquiryService;
    @Mock private ApplicationEventPublisher eventPublisher;

    // ──────────────── helpers ────────────────

    private Account createAccount(Long id, String name, String nickname) {
        Account account = Account.createUser(
                nickname + "@test.com", "encoded-pw", name, nickname, "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", id);
        return account;
    }

    private Store createStore(Long id, Account ownerAccount) {
        Store store = Store.createForOwnerSignup(ownerAccount, "테스트 스토어", "서울시 강남구", "02-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", id);
        return store;
    }

    private Inquiry createInquiry(Long id, Account writer, Store store, InquiryTargetType targetType) {
        Inquiry inquiry = Inquiry.create(writer, store, targetType,
                InquiryCategory.STORE, "문의 제목", "문의 내용", false);
        ReflectionTestUtils.setField(inquiry, "inquiryId", id);
        return inquiry;
    }

    private InquiryAnswerCreateRequestDto mockAnswerRequest(String content) {
        InquiryAnswerCreateRequestDto req = new InquiryAnswerCreateRequestDto();
        ReflectionTestUtils.setField(req, "content", content);
        return req;
    }

    // ──────────────── getStoreInquiries ────────────────

    @Test
    void 상점_문의_목록을_정상_조회한다() {
        // given
        Long ownerId = 1L;
        Long storeId = 10L;
        PageRequest pageable = PageRequest.of(0, 10);
        Account owner = createAccount(ownerId, "사장님", "owner_nick");
        Account writer = createAccount(2L, "사용자", "user_nick");
        Store store = createStore(storeId, owner);
        Inquiry inquiry = createInquiry(1L, writer, store, InquiryTargetType.STORE);
        Page<Inquiry> page = new PageImpl<>(List.of(inquiry));

        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.of(store));
        when(inquiryRepository.findByStore_StoreId(eq(storeId), eq(pageable))).thenReturn(page);

        // when
        Page<InquiryResponseDto> result = ownerInquiryService.getStoreInquiries(ownerId, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getStoreId()).isEqualTo(storeId);
    }

    @Test
    void 상점이_없는_사장이_문의_목록_조회하면_STORE_NOT_FOUND() {
        // given
        Long ownerId = 999L;
        PageRequest pageable = PageRequest.of(0, 10);
        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ownerInquiryService.getStoreInquiries(ownerId, pageable))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    // ──────────────── getStoreInquiryDetail ────────────────

    @Test
    void 상점_문의_상세를_정상_조회한다() {
        // given
        Long ownerId = 1L;
        Long storeId = 10L;
        Long inquiryId = 100L;
        Account owner = createAccount(ownerId, "사장님", "owner_nick");
        Account writer = createAccount(2L, "사용자", "user_nick");
        Store store = createStore(storeId, owner);
        Inquiry inquiry = createInquiry(inquiryId, writer, store, InquiryTargetType.STORE);

        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.of(store));
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));
        when(inquiryService.getAnswerDtos(eq(inquiryId))).thenReturn(List.of());

        // when
        InquiryDetailResponseDto result = ownerInquiryService.getStoreInquiryDetail(ownerId, inquiryId);

        // then
        assertThat(result.getInquiryId()).isEqualTo(inquiryId);
        assertThat(result.getStoreId()).isEqualTo(storeId);
    }

    @Test
    void 상점_문의_상세_조회_시_문의가_없으면_INQUIRY_NOT_FOUND() {
        // given
        Long ownerId = 1L;
        Long storeId = 10L;
        Long inquiryId = 999L;
        Account owner = createAccount(ownerId, "사장님", "owner_nick");
        Store store = createStore(storeId, owner);

        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.of(store));
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ownerInquiryService.getStoreInquiryDetail(ownerId, inquiryId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
    }

    @Test
    void 다른_상점의_문의를_사장이_조회하면_INQUIRY_ACCESS_DENIED() {
        // given
        Long ownerId = 1L;
        Long myStoreId = 10L;
        Long otherStoreId = 20L;
        Long inquiryId = 100L;
        Account owner = createAccount(ownerId, "사장님", "owner_nick");
        Account otherOwner = createAccount(2L, "다른사장", "other_owner");
        Account writer = createAccount(3L, "사용자", "user_nick");
        Store myStore = createStore(myStoreId, owner);
        Store otherStore = createStore(otherStoreId, otherOwner);
        Inquiry inquiry = createInquiry(inquiryId, writer, otherStore, InquiryTargetType.STORE);

        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.of(myStore));
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));

        // when & then
        assertThatThrownBy(() -> ownerInquiryService.getStoreInquiryDetail(ownerId, inquiryId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_ACCESS_DENIED);
    }

    @Test
    void store가_null인_문의를_사장이_조회하면_INQUIRY_ACCESS_DENIED() {
        // given
        Long ownerId = 1L;
        Long storeId = 10L;
        Long inquiryId = 100L;
        Account owner = createAccount(ownerId, "사장님", "owner_nick");
        Account writer = createAccount(2L, "사용자", "user_nick");
        Store store = createStore(storeId, owner);
        // store = null인 ADMIN 타입 문의
        Inquiry inquiry = createInquiry(inquiryId, writer, null, InquiryTargetType.ADMIN);

        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.of(store));
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));

        // when & then
        assertThatThrownBy(() -> ownerInquiryService.getStoreInquiryDetail(ownerId, inquiryId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_ACCESS_DENIED);
    }

    // ──────────────── answerInquiry ────────────────

    @Test
    void 사장_답변_정상_저장_후_문의_상태가_ANSWERED로_변경되고_이벤트를_발행한다() {
        // given
        Long ownerId = 1L;
        Long storeId = 10L;
        Long inquiryId = 100L;
        Account owner = createAccount(ownerId, "사장님", "owner_nick");
        Account writer = createAccount(2L, "사용자", "user_nick");
        Store store = createStore(storeId, owner);
        Inquiry inquiry = createInquiry(inquiryId, writer, store, InquiryTargetType.STORE);
        InquiryAnswerCreateRequestDto request = mockAnswerRequest("사장님 답변 내용");

        InquiryAnswer savedAnswer = InquiryAnswer.create(inquiry, owner, InquiryAnswerWriterType.OWNER, "사장님 답변 내용");
        ReflectionTestUtils.setField(savedAnswer, "answerId", 1L);

        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.of(store));
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));
        when(accountRepository.findById(eq(ownerId))).thenReturn(Optional.of(owner));
        when(inquiryAnswerRepository.save(any(InquiryAnswer.class))).thenReturn(savedAnswer);

        // when
        InquiryAnswerResponseDto result = ownerInquiryService.answerInquiry(ownerId, inquiryId, request);

        // then
        assertThat(result.getWriterType()).isEqualTo(InquiryAnswerWriterType.OWNER);
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
        verify(inquiryAnswerRepository).save(any(InquiryAnswer.class));
        verify(eventPublisher).publishEvent(any(InquiryAnsweredEvent.class));
    }

    @Test
    void 사장_답변_시_상점이_없으면_STORE_NOT_FOUND() {
        // given
        Long ownerId = 999L;
        Long inquiryId = 100L;
        InquiryAnswerCreateRequestDto request = mockAnswerRequest("답변");
        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ownerInquiryService.answerInquiry(ownerId, inquiryId, request))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    void 사장_답변_시_문의가_없으면_INQUIRY_NOT_FOUND() {
        // given
        Long ownerId = 1L;
        Long storeId = 10L;
        Long inquiryId = 999L;
        Account owner = createAccount(ownerId, "사장님", "owner_nick");
        Store store = createStore(storeId, owner);
        InquiryAnswerCreateRequestDto request = mockAnswerRequest("답변");

        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.of(store));
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ownerInquiryService.answerInquiry(ownerId, inquiryId, request))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
    }

    @Test
    void 사장이_다른_상점의_문의에_답변하면_INQUIRY_ACCESS_DENIED() {
        // given
        Long ownerId = 1L;
        Long myStoreId = 10L;
        Long otherStoreId = 20L;
        Long inquiryId = 100L;
        Account owner = createAccount(ownerId, "사장님", "owner_nick");
        Account otherOwner = createAccount(2L, "다른사장", "other_owner");
        Account writer = createAccount(3L, "사용자", "user_nick");
        Store myStore = createStore(myStoreId, owner);
        Store otherStore = createStore(otherStoreId, otherOwner);
        Inquiry inquiry = createInquiry(inquiryId, writer, otherStore, InquiryTargetType.STORE);
        InquiryAnswerCreateRequestDto request = mockAnswerRequest("답변");

        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.of(myStore));
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));

        // when & then
        assertThatThrownBy(() -> ownerInquiryService.answerInquiry(ownerId, inquiryId, request))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_ACCESS_DENIED);
    }

    @Test
    void 사장이_ADMIN_타입_문의에_답변하면_INQUIRY_TARGET_TYPE_MISMATCH() {
        // given
        Long ownerId = 1L;
        Long storeId = 10L;
        Long inquiryId = 100L;
        Account owner = createAccount(ownerId, "사장님", "owner_nick");
        Account writer = createAccount(2L, "사용자", "user_nick");
        Store store = createStore(storeId, owner);
        // store는 같지만 targetType이 ADMIN → 사장이 답변 불가
        Inquiry inquiry = createInquiry(inquiryId, writer, store, InquiryTargetType.ADMIN);
        InquiryAnswerCreateRequestDto request = mockAnswerRequest("답변");

        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.of(store));
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));

        // when & then
        assertThatThrownBy(() -> ownerInquiryService.answerInquiry(ownerId, inquiryId, request))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
    }

    @Test
    void 이미_답변된_문의에_사장이_재답변하면_INQUIRY_ALREADY_ANSWERED() {
        // given
        Long ownerId = 1L;
        Long storeId = 10L;
        Long inquiryId = 100L;
        Account owner = createAccount(ownerId, "사장님", "owner_nick");
        Account writer = createAccount(2L, "사용자", "user_nick");
        Store store = createStore(storeId, owner);
        Inquiry inquiry = createInquiry(inquiryId, writer, store, InquiryTargetType.STORE);
        inquiry.markAnswered(); // status = ANSWERED → isAnswerable() = false
        InquiryAnswerCreateRequestDto request = mockAnswerRequest("답변");

        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.of(store));
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));

        // when & then
        assertThatThrownBy(() -> ownerInquiryService.answerInquiry(ownerId, inquiryId, request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_ALREADY_ANSWERED);
    }

    @Test
    void 사장_답변_시_사장_계정이_없으면_ACCOUNT_NOT_FOUND() {
        // given
        Long ownerId = 1L;
        Long storeId = 10L;
        Long inquiryId = 100L;
        Account owner = createAccount(ownerId, "사장님", "owner_nick");
        Account writer = createAccount(2L, "사용자", "user_nick");
        Store store = createStore(storeId, owner);
        Inquiry inquiry = createInquiry(inquiryId, writer, store, InquiryTargetType.STORE);
        InquiryAnswerCreateRequestDto request = mockAnswerRequest("답변");

        when(storeRepository.findByAccount_AccountId(eq(ownerId))).thenReturn(Optional.of(store));
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));
        when(accountRepository.findById(eq(ownerId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ownerInquiryService.answerInquiry(ownerId, inquiryId, request))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }
}
