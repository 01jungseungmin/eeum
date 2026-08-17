package com.eeum.eeum.application.inquiry.service;

import com.eeum.eeum.application.inquiry.dto.request.InquiryAnswerCreateRequestDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryAnswerResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.entity.InquiryAnswer;
import com.eeum.eeum.domain.inquiry.enums.InquiryAnswerWriterType;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.repository.InquiryAnswerRepository;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInquiryLifecycleTest {

    private static final Long INQUIRY_ID = 1L;
    private static final Long ANSWER_ID = 10L;

    @Mock private InquiryRepository inquiryRepository;
    @Mock private InquiryAnswerRepository inquiryAnswerRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private InquiryService inquiryService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AdminInquiryService adminInquiryService;

    // ─────────────────── 종료 ───────────────────

    @Test
    void 관리자_문의를_종료하면_상태가_CLOSED가_된다() {
        // given
        Inquiry inquiry = adminInquiry(InquiryStatus.PENDING);
        when(inquiryRepository.findByInquiryId(INQUIRY_ID)).thenReturn(Optional.of(inquiry));

        // when
        adminInquiryService.closeInquiry(INQUIRY_ID);

        // then
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.CLOSED);
    }

    @Test
    void 이미_종료된_문의를_다시_종료하면_거부된다() {
        Inquiry inquiry = adminInquiry(InquiryStatus.CLOSED);
        when(inquiryRepository.findByInquiryId(INQUIRY_ID)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> adminInquiryService.closeInquiry(INQUIRY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_ALREADY_CLOSED);
    }

    @Test
    void 상점_문의는_관리자_종료_대상이_아니다() {
        Inquiry inquiry = adminInquiry(InquiryStatus.PENDING);
        ReflectionTestUtils.setField(inquiry, "targetType", InquiryTargetType.STORE);
        when(inquiryRepository.findByInquiryId(INQUIRY_ID)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> adminInquiryService.closeInquiry(INQUIRY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
    }

    // ─────────────────── 재오픈 ───────────────────

    @Test
    void 종료된_문의를_재오픈하면_PENDING으로_돌아가_미답변_목록에_다시_잡힌다() {
        Inquiry inquiry = adminInquiry(InquiryStatus.CLOSED);
        when(inquiryRepository.findByInquiryId(INQUIRY_ID)).thenReturn(Optional.of(inquiry));

        adminInquiryService.reopenInquiry(INQUIRY_ID);

        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.PENDING);
        assertThat(inquiry.isAnswerable()).isTrue();
    }

    @Test
    void 종료_상태가_아닌_문의의_재오픈은_거부된다() {
        Inquiry inquiry = adminInquiry(InquiryStatus.ANSWERED);
        when(inquiryRepository.findByInquiryId(INQUIRY_ID)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> adminInquiryService.reopenInquiry(INQUIRY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_NOT_CLOSED);
    }

    // ─────────────────── 종료 문의 답변 차단 ───────────────────

    @Test
    void 종료된_문의에는_답변할_수_없고_이미답변과_다른_코드를_반환한다() {
        Inquiry inquiry = adminInquiry(InquiryStatus.CLOSED);
        when(inquiryRepository.findByInquiryId(INQUIRY_ID)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> adminInquiryService.answerInquiry(
                99L, INQUIRY_ID, answerRequest("답변 시도")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_CLOSED);
    }

    @Test
    void 재오픈된_문의에_답변이_남아있으면_재답변_대신_수정을_요구한다() {
        // given — 답변까지 끝난 뒤 종료됐다가 재오픈되어 상태만 PENDING으로 돌아온 문의
        Inquiry inquiry = adminInquiry(InquiryStatus.PENDING);
        when(inquiryRepository.findByInquiryId(INQUIRY_ID)).thenReturn(Optional.of(inquiry));
        when(inquiryAnswerRepository.existsByInquiry_InquiryId(INQUIRY_ID)).thenReturn(true);

        // when & then — DB 유니크 제약이 아니라 비즈니스 에러로 걸러야 한다
        assertThatThrownBy(() -> adminInquiryService.answerInquiry(
                99L, INQUIRY_ID, answerRequest("두 번째 답변")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_ALREADY_ANSWERED);
    }

    // ─────────────────── 답변 수정 ───────────────────

    @Test
    void 종료된_문의의_답변도_정정할_수_있다() {
        // given — 종료는 새 답변만 막고, 이미 나간 잘못된 안내의 정정은 막지 않는다
        Inquiry inquiry = adminInquiry(InquiryStatus.CLOSED);
        InquiryAnswer answer = adminAnswer(inquiry, "잘못된 안내");
        when(inquiryRepository.findByInquiryId(INQUIRY_ID)).thenReturn(Optional.of(inquiry));
        when(inquiryAnswerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer));

        // when
        InquiryAnswerResponseDto result =
                adminInquiryService.updateAnswer(INQUIRY_ID, ANSWER_ID, answerRequest("바로잡은 안내"));

        // then
        assertThat(result.getContent()).isEqualTo("바로잡은 안내");
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.CLOSED);
    }

    @Test
    void 관리자_답변을_수정하면_본문이_바뀐다() {
        Inquiry inquiry = adminInquiry(InquiryStatus.ANSWERED);
        InquiryAnswer answer = adminAnswer(inquiry, "오타가 있던 답변");
        when(inquiryRepository.findByInquiryId(INQUIRY_ID)).thenReturn(Optional.of(inquiry));
        when(inquiryAnswerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer));

        InquiryAnswerResponseDto result =
                adminInquiryService.updateAnswer(INQUIRY_ID, ANSWER_ID, answerRequest("정정된 답변"));

        assertThat(answer.getContent()).isEqualTo("정정된 답변");
        assertThat(result.getContent()).isEqualTo("정정된 답변");
    }

    @Test
    void 다른_문의의_답변_ID로는_수정할_수_없다() {
        Inquiry inquiry = adminInquiry(InquiryStatus.ANSWERED);
        Inquiry otherInquiry = adminInquiry(InquiryStatus.ANSWERED);
        ReflectionTestUtils.setField(otherInquiry, "inquiryId", 999L);
        InquiryAnswer answerOfOther = adminAnswer(otherInquiry, "남의 문의 답변");

        when(inquiryRepository.findByInquiryId(INQUIRY_ID)).thenReturn(Optional.of(inquiry));
        when(inquiryAnswerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answerOfOther));

        assertThatThrownBy(() -> adminInquiryService.updateAnswer(
                INQUIRY_ID, ANSWER_ID, answerRequest("바꿔치기 시도")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_ANSWER_NOT_FOUND);
    }

    @Test
    void 관리자_API로_사장_답변은_수정할_수_없다() {
        Inquiry inquiry = adminInquiry(InquiryStatus.ANSWERED);
        InquiryAnswer ownerAnswer = adminAnswer(inquiry, "사장이 쓴 답변");
        ReflectionTestUtils.setField(ownerAnswer, "writerType", InquiryAnswerWriterType.OWNER);

        when(inquiryRepository.findByInquiryId(INQUIRY_ID)).thenReturn(Optional.of(inquiry));
        when(inquiryAnswerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(ownerAnswer));

        assertThatThrownBy(() -> adminInquiryService.updateAnswer(
                INQUIRY_ID, ANSWER_ID, answerRequest("월권 수정")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
    }

    // ─────────────────── 헬퍼 ───────────────────

    private Inquiry adminInquiry(InquiryStatus status) {
        Account writer = Account.createUser("user@test.com", "pw", "문의자", "문의자닉", "010-0000-0000");
        ReflectionTestUtils.setField(writer, "accountId", 5L);

        Inquiry inquiry = Inquiry.create(writer, null, InquiryTargetType.ADMIN,
                InquiryCategory.ETC, "제목", "본문", false);
        ReflectionTestUtils.setField(inquiry, "inquiryId", INQUIRY_ID);
        ReflectionTestUtils.setField(inquiry, "status", status);
        return inquiry;
    }

    private InquiryAnswer adminAnswer(Inquiry inquiry, String content) {
        Account admin = Account.createUser("admin@test.com", "pw", "관리자", "관리자닉", "010-1111-1111");
        ReflectionTestUtils.setField(admin, "accountId", 99L);

        InquiryAnswer answer = InquiryAnswer.create(
                inquiry, admin, InquiryAnswerWriterType.ADMIN, content);
        ReflectionTestUtils.setField(answer, "answerId", ANSWER_ID);
        return answer;
    }

    private InquiryAnswerCreateRequestDto answerRequest(String content) {
        InquiryAnswerCreateRequestDto request = new InquiryAnswerCreateRequestDto();
        ReflectionTestUtils.setField(request, "content", content);
        return request;
    }
}
