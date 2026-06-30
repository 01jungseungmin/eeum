package com.eeum.eeum.application.inquiry.service;

import com.eeum.eeum.application.inquiry.dto.request.InquiryCreateRequestDto;
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
import com.eeum.eeum.domain.inquiry.event.InquirySubmittedEvent;
import com.eeum.eeum.domain.inquiry.repository.InquiryAnswerRepository;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BadRequestException;
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
class InquiryServiceTest {

    @InjectMocks private InquiryService inquiryService;

    @Mock private InquiryRepository inquiryRepository;
    @Mock private InquiryAnswerRepository inquiryAnswerRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private StoreRepository storeRepository;
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
                InquiryCategory.ORDER, "문의 제목", "문의 내용", false);
        ReflectionTestUtils.setField(inquiry, "inquiryId", id);
        return inquiry;
    }

    private InquiryCreateRequestDto mockRequest(InquiryTargetType targetType, Long storeId) {
        InquiryCreateRequestDto req = new InquiryCreateRequestDto();
        ReflectionTestUtils.setField(req, "targetType", targetType);
        ReflectionTestUtils.setField(req, "category", InquiryCategory.ORDER);
        ReflectionTestUtils.setField(req, "storeId", storeId);
        ReflectionTestUtils.setField(req, "title", "문의 제목");
        ReflectionTestUtils.setField(req, "content", "문의 내용");
        ReflectionTestUtils.setField(req, "secret", false);
        return req;
    }

    // ──────────────── createInquiry ────────────────

    @Test
    void STORE_타입_문의_정상_생성_시_저장하고_이벤트를_발행한다() {
        // given
        Long accountId = 1L;
        Long storeId = 10L;
        Account ownerAccount = createAccount(99L, "사장님", "owner_nick");
        Account writer = createAccount(accountId, "사용자", "user_nick");
        Store store = createStore(storeId, ownerAccount);
        InquiryCreateRequestDto request = mockRequest(InquiryTargetType.STORE, storeId);

        Inquiry savedInquiry = createInquiry(100L, writer, store, InquiryTargetType.STORE);
        when(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(writer));
        when(storeRepository.findById(eq(storeId))).thenReturn(Optional.of(store));
        when(inquiryRepository.save(any(Inquiry.class))).thenReturn(savedInquiry);

        // when
        InquiryResponseDto result = inquiryService.createInquiry(accountId, request);

        // then
        assertThat(result.getInquiryId()).isEqualTo(100L);
        assertThat(result.getTargetType()).isEqualTo(InquiryTargetType.STORE);
        assertThat(result.getStatus()).isEqualTo(InquiryStatus.PENDING);
        verify(inquiryRepository).save(any(Inquiry.class));
        verify(eventPublisher).publishEvent(any(InquirySubmittedEvent.class));
    }

    @Test
    void ADMIN_타입_문의_정상_생성_시_저장하고_이벤트를_발행한다() {
        // given
        Long accountId = 1L;
        Account writer = createAccount(accountId, "사용자", "user_nick");
        InquiryCreateRequestDto request = mockRequest(InquiryTargetType.ADMIN, null);

        Inquiry savedInquiry = createInquiry(101L, writer, null, InquiryTargetType.ADMIN);
        when(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(writer));
        when(inquiryRepository.save(any(Inquiry.class))).thenReturn(savedInquiry);

        // when
        InquiryResponseDto result = inquiryService.createInquiry(accountId, request);

        // then
        assertThat(result.getInquiryId()).isEqualTo(101L);
        assertThat(result.getTargetType()).isEqualTo(InquiryTargetType.ADMIN);
        assertThat(result.getStoreId()).isNull();
        verify(eventPublisher).publishEvent(any(InquirySubmittedEvent.class));
    }

    @Test
    void 문의_생성_시_작성자가_없으면_ACCOUNT_NOT_FOUND() {
        // given
        Long accountId = 999L;
        InquiryCreateRequestDto request = mockRequest(InquiryTargetType.ADMIN, null);
        when(accountRepository.findById(eq(accountId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> inquiryService.createInquiry(accountId, request))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void STORE_타입_문의인데_storeId가_null이면_INQUIRY_STORE_REQUIRED() {
        // given
        Long accountId = 1L;
        Account writer = createAccount(accountId, "사용자", "user_nick");
        InquiryCreateRequestDto request = mockRequest(InquiryTargetType.STORE, null);
        when(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(writer));

        // when & then
        assertThatThrownBy(() -> inquiryService.createInquiry(accountId, request))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_STORE_REQUIRED);
    }

    @Test
    void STORE_타입_문의인데_상점이_존재하지_않으면_STORE_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Long storeId = 999L;
        Account writer = createAccount(accountId, "사용자", "user_nick");
        InquiryCreateRequestDto request = mockRequest(InquiryTargetType.STORE, storeId);
        when(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(writer));
        when(storeRepository.findById(eq(storeId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> inquiryService.createInquiry(accountId, request))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    void ADMIN_타입_문의에_storeId를_포함하면_INQUIRY_STORE_NOT_ALLOWED() {
        // given
        Long accountId = 1L;
        Account writer = createAccount(accountId, "사용자", "user_nick");
        InquiryCreateRequestDto request = mockRequest(InquiryTargetType.ADMIN, 10L);
        when(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(writer));

        // when & then
        assertThatThrownBy(() -> inquiryService.createInquiry(accountId, request))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_STORE_NOT_ALLOWED);
    }

    // ──────────────── getMyInquiries ────────────────

    @Test
    void 내_문의_목록_조회가_정상_동작한다() {
        // given
        Long accountId = 1L;
        PageRequest pageable = PageRequest.of(0, 10);
        Account writer = createAccount(accountId, "사용자", "user_nick");
        Inquiry inquiry = createInquiry(1L, writer, null, InquiryTargetType.ADMIN);
        Page<Inquiry> inquiryPage = new PageImpl<>(List.of(inquiry));
        when(inquiryRepository.findByWriter_AccountId(eq(accountId), eq(pageable))).thenReturn(inquiryPage);

        // when
        Page<InquiryResponseDto> result = inquiryService.getMyInquiries(accountId, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTargetType()).isEqualTo(InquiryTargetType.ADMIN);
    }

    // ──────────────── getMyInquiryDetail ────────────────

    @Test
    void 내_문의_상세_조회가_정상_동작한다() {
        // given
        Long accountId = 1L;
        Long inquiryId = 10L;
        Account writer = createAccount(accountId, "사용자", "user_nick");
        Inquiry inquiry = createInquiry(inquiryId, writer, null, InquiryTargetType.ADMIN);
        InquiryAnswer answer = InquiryAnswer.create(inquiry, writer, InquiryAnswerWriterType.ADMIN, "답변 내용");
        ReflectionTestUtils.setField(answer, "answerId", 1L);

        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));
        when(inquiryAnswerRepository.findByInquiry_InquiryIdOrderByCreatedAtAsc(eq(inquiryId)))
                .thenReturn(List.of(answer));

        // when
        InquiryDetailResponseDto result = inquiryService.getMyInquiryDetail(accountId, inquiryId);

        // then
        assertThat(result.getInquiryId()).isEqualTo(inquiryId);
        assertThat(result.getAnswers()).hasSize(1);
    }

    @Test
    void 내_문의_상세_조회_시_문의가_없으면_INQUIRY_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Long inquiryId = 999L;
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> inquiryService.getMyInquiryDetail(accountId, inquiryId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
    }

    @Test
    void 다른_사람의_문의를_조회하면_INQUIRY_ACCESS_DENIED() {
        // given
        Long requesterId = 1L;
        Long writerId = 2L;
        Long inquiryId = 10L;
        Account writer = createAccount(writerId, "다른 사용자", "other_nick");
        Inquiry inquiry = createInquiry(inquiryId, writer, null, InquiryTargetType.ADMIN);
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));

        // when & then
        assertThatThrownBy(() -> inquiryService.getMyInquiryDetail(requesterId, inquiryId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_ACCESS_DENIED);
    }
}
