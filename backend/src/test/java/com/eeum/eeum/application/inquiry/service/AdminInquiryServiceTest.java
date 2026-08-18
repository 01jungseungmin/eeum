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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInquiryServiceTest {

    @InjectMocks private AdminInquiryService adminInquiryService;

    @Mock private InquiryRepository inquiryRepository;
    @Mock private InquiryAnswerRepository inquiryAnswerRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private InquiryService inquiryService;
    @Mock private ApplicationEventPublisher eventPublisher;

    // ──────────────── helpers ────────────────

    private Account createAccount(Long id, String name, String nickname) {
        Account account = Account.createUser(
                nickname + "@test.com", "encoded-pw", name, nickname, "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", id);
        return account;
    }

    private Inquiry createInquiry(Long id, Account writer, InquiryTargetType targetType) {
        Inquiry inquiry = Inquiry.create(writer, null, targetType,
                InquiryCategory.ORDER, "문의 제목", "문의 내용", false);
        ReflectionTestUtils.setField(inquiry, "inquiryId", id);
        return inquiry;
    }

    private InquiryAnswerCreateRequestDto mockAnswerRequest(String content) {
        InquiryAnswerCreateRequestDto req = new InquiryAnswerCreateRequestDto();
        ReflectionTestUtils.setField(req, "content", content);
        return req;
    }

    // ──────────────── getAdminInquiries ────────────────

    @Test
    void 관리자_문의_목록을_정상_조회한다() {
        // given
        PageRequest pageable = PageRequest.of(0, 10);
        Account writer = createAccount(1L, "사용자", "user_nick");
        Inquiry inquiry = createInquiry(1L, writer, InquiryTargetType.ADMIN);
        Page<Inquiry> page = new PageImpl<>(List.of(inquiry));
        when(inquiryRepository.searchInquiries(
                eq(InquiryTargetType.ADMIN), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(page);

        // when: 필터 미지정이면 전체 조회
        Page<InquiryResponseDto> result =
                adminInquiryService.getAdminInquiries(null, null, null, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTargetType()).isEqualTo(InquiryTargetType.ADMIN);
    }

    // ──────────────── getAdminInquiryDetail ────────────────

    @Test
    void 관리자_문의_상세를_정상_조회한다() {
        // given
        Long inquiryId = 10L;
        Account writer = createAccount(1L, "사용자", "user_nick");
        Inquiry inquiry = createInquiry(inquiryId, writer, InquiryTargetType.ADMIN);
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));
        when(inquiryService.getAnswerDtos(eq(inquiryId))).thenReturn(List.of());

        // when
        InquiryDetailResponseDto result = adminInquiryService.getAdminInquiryDetail(inquiryId);

        // then
        assertThat(result.getInquiryId()).isEqualTo(inquiryId);
        assertThat(result.getTargetType()).isEqualTo(InquiryTargetType.ADMIN);
    }

    @Test
    void 관리자_문의_상세_조회_시_문의가_없으면_INQUIRY_NOT_FOUND() {
        // given
        Long inquiryId = 999L;
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminInquiryService.getAdminInquiryDetail(inquiryId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
    }

    @Test
    void STORE_타입_문의를_관리자_상세_조회하면_INQUIRY_TARGET_TYPE_MISMATCH() {
        // given
        Long inquiryId = 10L;
        Account writer = createAccount(1L, "사용자", "user_nick");
        Inquiry inquiry = createInquiry(inquiryId, writer, InquiryTargetType.STORE);
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));

        // when & then
        assertThatThrownBy(() -> adminInquiryService.getAdminInquiryDetail(inquiryId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
    }

    // ──────────────── answerInquiry ────────────────

    @Test
    void 관리자_답변_정상_저장_후_문의_상태가_ANSWERED로_변경되고_이벤트를_발행한다() {
        // given
        Long adminId = 99L;
        Long inquiryId = 10L;
        Account writer = createAccount(1L, "사용자", "user_nick");
        Account admin = createAccount(adminId, "관리자", "admin_nick");
        Inquiry inquiry = createInquiry(inquiryId, writer, InquiryTargetType.ADMIN);
        InquiryAnswerCreateRequestDto request = mockAnswerRequest("관리자 답변 내용");

        InquiryAnswer savedAnswer = InquiryAnswer.create(inquiry, admin, InquiryAnswerWriterType.ADMIN, "관리자 답변 내용");
        ReflectionTestUtils.setField(savedAnswer, "answerId", 1L);

        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));
        when(accountRepository.findById(eq(adminId))).thenReturn(Optional.of(admin));
        when(inquiryAnswerRepository.save(any(InquiryAnswer.class))).thenReturn(savedAnswer);

        // when
        InquiryAnswerResponseDto result = adminInquiryService.answerInquiry(adminId, inquiryId, request);

        // then
        assertThat(result.getWriterType()).isEqualTo(InquiryAnswerWriterType.ADMIN);
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
        verify(inquiryAnswerRepository).save(any(InquiryAnswer.class));
        verify(eventPublisher).publishEvent(any(InquiryAnsweredEvent.class));
    }

    @Test
    void 관리자_답변_시_문의가_없으면_INQUIRY_NOT_FOUND() {
        // given
        Long adminId = 99L;
        Long inquiryId = 999L;
        InquiryAnswerCreateRequestDto request = mockAnswerRequest("답변");
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminInquiryService.answerInquiry(adminId, inquiryId, request))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
    }

    @Test
    void 관리자가_STORE_타입_문의에_답변하면_INQUIRY_TARGET_TYPE_MISMATCH() {
        // given
        Long adminId = 99L;
        Long inquiryId = 10L;
        Account writer = createAccount(1L, "사용자", "user_nick");
        Inquiry inquiry = createInquiry(inquiryId, writer, InquiryTargetType.STORE);
        InquiryAnswerCreateRequestDto request = mockAnswerRequest("답변");
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));

        // when & then
        assertThatThrownBy(() -> adminInquiryService.answerInquiry(adminId, inquiryId, request))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
    }

    @Test
    void 이미_답변된_문의에_관리자가_재답변하면_INQUIRY_ALREADY_ANSWERED() {
        // given
        Long adminId = 99L;
        Long inquiryId = 10L;
        Account writer = createAccount(1L, "사용자", "user_nick");
        Inquiry inquiry = createInquiry(inquiryId, writer, InquiryTargetType.ADMIN);
        inquiry.markAnswered(); // status = ANSWERED → isAnswerable() = false
        InquiryAnswerCreateRequestDto request = mockAnswerRequest("답변");
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));

        // when & then
        assertThatThrownBy(() -> adminInquiryService.answerInquiry(adminId, inquiryId, request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_ALREADY_ANSWERED);
    }

    @Test
    void 관리자_답변_시_관리자_계정이_없으면_ACCOUNT_NOT_FOUND() {
        // given
        Long adminId = 999L;
        Long inquiryId = 10L;
        Account writer = createAccount(1L, "사용자", "user_nick");
        Inquiry inquiry = createInquiry(inquiryId, writer, InquiryTargetType.ADMIN);
        InquiryAnswerCreateRequestDto request = mockAnswerRequest("답변");
        when(inquiryRepository.findByInquiryId(eq(inquiryId))).thenReturn(Optional.of(inquiry));
        when(accountRepository.findById(eq(adminId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminInquiryService.answerInquiry(adminId, inquiryId, request))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }
}
