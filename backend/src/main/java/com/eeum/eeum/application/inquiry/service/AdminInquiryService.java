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
    public Page<InquiryResponseDto> getAdminInquiries(Pageable pageable) {
        return inquiryRepository.findByTargetType(InquiryTargetType.ADMIN, pageable)
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
        if (!inquiry.isAnswerable()) {
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
}
