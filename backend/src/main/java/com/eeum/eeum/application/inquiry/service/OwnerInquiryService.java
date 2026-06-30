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
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
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
public class OwnerInquiryService {

    private final InquiryRepository inquiryRepository;
    private final InquiryAnswerRepository inquiryAnswerRepository;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final InquiryService inquiryService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<InquiryResponseDto> getStoreInquiries(Long ownerId, Pageable pageable) {
        Long storeId = getOwnerStoreId(ownerId);
        return inquiryRepository.findByStore_StoreId(storeId, pageable)
                .map(InquiryResponseDto::from);
    }

    @Transactional(readOnly = true)
    public InquiryDetailResponseDto getStoreInquiryDetail(Long ownerId, Long inquiryId) {
        Long storeId = getOwnerStoreId(ownerId);
        Inquiry inquiry = inquiryRepository.findByInquiryId(inquiryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INQUIRY_NOT_FOUND));

        if (inquiry.getStore() == null || !inquiry.getStore().getStoreId().equals(storeId)) {
            throw new ForbiddenException(ErrorCode.INQUIRY_ACCESS_DENIED);
        }

        List<InquiryAnswerResponseDto> answers = inquiryService.getAnswerDtos(inquiryId);
        return InquiryDetailResponseDto.of(inquiry, answers);
    }

    @Transactional
    public InquiryAnswerResponseDto answerInquiry(Long ownerId, Long inquiryId,
                                                   InquiryAnswerCreateRequestDto request) {
        Long storeId = getOwnerStoreId(ownerId);
        Inquiry inquiry = inquiryRepository.findByInquiryId(inquiryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INQUIRY_NOT_FOUND));

        if (inquiry.getStore() == null || !inquiry.getStore().getStoreId().equals(storeId)) {
            throw new ForbiddenException(ErrorCode.INQUIRY_ACCESS_DENIED);
        }
        if (inquiry.getTargetType() != InquiryTargetType.STORE) {
            throw new ForbiddenException(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
        }
        if (!inquiry.isAnswerable()) {
            throw new ConflictException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }

        Account owner = accountRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));

        InquiryAnswer answer = InquiryAnswer.create(inquiry, owner, InquiryAnswerWriterType.OWNER, request.getContent());
        InquiryAnswer saved = inquiryAnswerRepository.save(answer);
        inquiry.markAnswered();

        eventPublisher.publishEvent(new InquiryAnsweredEvent(
                inquiry.getInquiryId(),
                inquiry.getWriter().getAccountId(),
                inquiry.getTitle()
        ));

        return InquiryAnswerResponseDto.from(saved);
    }

    // ===================== 내부 유틸 =====================

    private Long getOwnerStoreId(Long ownerId) {
        return storeRepository.findByAccount_AccountId(ownerId)
                .map(Store::getStoreId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.STORE_NOT_FOUND));
    }
}
