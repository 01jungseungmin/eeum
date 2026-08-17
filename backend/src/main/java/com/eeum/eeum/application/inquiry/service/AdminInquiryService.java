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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminInquiryService {

    private final InquiryRepository inquiryRepository;
    private final InquiryAnswerRepository inquiryAnswerRepository;
    private final AccountRepository accountRepository;
    private final InquiryService inquiryService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<InquiryResponseDto> getAdminInquiries(
            InquiryStatus status,
            InquiryCategory category,
            String keyword,
            Pageable pageable
    ) {
        return inquiryRepository
                .searchInquiries(InquiryTargetType.ADMIN, status, category, keyword, pageable)
                .map(InquiryResponseDto::from);
    }

    @Transactional(readOnly = true)
    public InquiryDetailResponseDto getAdminInquiryDetail(Long inquiryId) {
        Inquiry inquiry = inquiryRepository.findByInquiryId(inquiryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INQUIRY_NOT_FOUND));

        if (inquiry.getTargetType() != InquiryTargetType.ADMIN) {
            throw new ForbiddenException(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
        }

        List<InquiryAnswerResponseDto> answers = inquiryService.getAnswerDtos(inquiryId);
        return InquiryDetailResponseDto.of(inquiry, answers);
    }

    @Transactional
    public InquiryAnswerResponseDto answerInquiry(Long adminId, Long inquiryId,
                                                   InquiryAnswerCreateRequestDto request) {
        Inquiry inquiry = inquiryRepository.findByInquiryId(inquiryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INQUIRY_NOT_FOUND));

        if (inquiry.getTargetType() != InquiryTargetType.ADMIN) {
            throw new ForbiddenException(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
        }
        if (inquiry.isClosed()) {
            throw new ConflictException(ErrorCode.INQUIRY_CLOSED);
        }
        if (!inquiry.isAnswerable()) {
            throw new ConflictException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }
        // 재오픈된 문의는 상태가 PENDING으로 돌아가지만 기존 답변은 그대로 남아 있다.
        // 답변은 문의당 1건이므로(uk_inquiry_answer_inquiry_id) 여기서 막지 않으면
        // DB 제약 위반이 그대로 새어 나간다. 정정이 필요하면 updateAnswer를 쓴다.
        if (inquiryAnswerRepository.existsByInquiry_InquiryId(inquiryId)) {
            throw new ConflictException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }

        Account admin = accountRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));

        InquiryAnswer answer = InquiryAnswer.create(inquiry, admin, InquiryAnswerWriterType.ADMIN, request.getContent());
        InquiryAnswer saved = inquiryAnswerRepository.save(answer);
        inquiry.markAnswered();

        eventPublisher.publishEvent(new InquiryAnsweredEvent(
                inquiry.getInquiryId(),
                inquiry.getWriter().getAccountId(),
                inquiry.getTitle()
        ));

        return InquiryAnswerResponseDto.from(saved);
    }

    /**
     * 관리자 답변 본문 정정.
     *
     * <p>삭제는 제공하지 않는다 — 사용자가 이미 알림으로 받아 본 답변이 통째로 사라지면
     * 문의 스레드의 문맥이 끊긴다. 답변이 불필요해진 문의는 {@link #closeInquiry(Long)}로 닫는다.
     *
     * <p>수정 시각은 BaseEntity가 자동 갱신하며, 응답 DTO의 {@code edited} 플래그로 노출된다.
     * 알림은 재발송하지 않는다 — 오타 정정마다 푸시가 나가면 알림 피로를 부른다.
     *
     * <p>종료된 문의의 답변도 정정할 수 있다 — 이미 사용자에게 노출된 잘못된 안내를 바로잡는 일은
     * 문의를 다시 여는 것과 무관하다. 새 답변을 다는 것만 종료 상태에서 막힌다.
     */
    @Transactional
    public InquiryAnswerResponseDto updateAnswer(
            Long inquiryId,
            Long answerId,
            InquiryAnswerCreateRequestDto request
    ) {
        Inquiry inquiry = getAdminInquiryOrThrow(inquiryId);

        InquiryAnswer answer = inquiryAnswerRepository.findById(answerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INQUIRY_ANSWER_NOT_FOUND));

        // 다른 문의의 답변 ID를 넘겨 남의 답변을 고치는 경로를 막는다.
        if (!answer.getInquiry().getInquiryId().equals(inquiry.getInquiryId())) {
            throw new NotFoundException(ErrorCode.INQUIRY_ANSWER_NOT_FOUND);
        }
        // 관리자 API로 사장 답변을 고칠 수 없다.
        if (!answer.isWrittenBy(InquiryAnswerWriterType.ADMIN)) {
            throw new ForbiddenException(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
        }

        answer.updateContent(request.getContent());
        return InquiryAnswerResponseDto.from(answer);
    }

    // ===================== 문의 종료 / 재오픈 =====================

    @Transactional
    public void closeInquiry(Long inquiryId) {
        Inquiry inquiry = getAdminInquiryOrThrow(inquiryId);
        if (inquiry.isClosed()) {
            throw new ConflictException(ErrorCode.INQUIRY_ALREADY_CLOSED);
        }
        inquiry.close();
    }

    @Transactional
    public void reopenInquiry(Long inquiryId) {
        Inquiry inquiry = getAdminInquiryOrThrow(inquiryId);
        if (!inquiry.isClosed()) {
            throw new ConflictException(ErrorCode.INQUIRY_NOT_CLOSED);
        }
        inquiry.reopen();
    }

    // ADMIN 문의가 아니면 관리자 API로 다룰 수 없다 — 상점 문의는 사장이 담당한다.
    private Inquiry getAdminInquiryOrThrow(Long inquiryId) {
        Inquiry inquiry = inquiryRepository.findByInquiryId(inquiryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INQUIRY_NOT_FOUND));
        if (inquiry.getTargetType() != InquiryTargetType.ADMIN) {
            throw new ForbiddenException(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
        }
        return inquiry;
    }
}
