package com.eeum.eeum.application.inquiry.service;

import com.eeum.eeum.application.inquiry.dto.request.InquiryCreateRequestDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryAnswerResponseDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryDetailResponseDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.entity.InquiryAnswer;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final InquiryAnswerRepository inquiryAnswerRepository;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public InquiryResponseDto createInquiry(Long accountId, InquiryCreateRequestDto request) {
        Account writer = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));

        Store store = null;

        if (request.getTargetType() == InquiryTargetType.STORE) {
            if (request.getStoreId() == null) {
                throw new BadRequestException(ErrorCode.INQUIRY_STORE_REQUIRED);
            }
            store = storeRepository.findById(request.getStoreId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.STORE_NOT_FOUND));
        }

        if (request.getTargetType() == InquiryTargetType.ADMIN && request.getStoreId() != null) {
            throw new BadRequestException(ErrorCode.INQUIRY_STORE_NOT_ALLOWED);
        }

        Inquiry inquiry = Inquiry.create(
                writer,
                store,
                request.getTargetType(),
                request.getCategory(),
                request.getTitle(),
                request.getContent(),
                request.isSecret()
        );

        Inquiry saved = inquiryRepository.save(inquiry);

        eventPublisher.publishEvent(new InquirySubmittedEvent(
                saved.getInquiryId(),
                request.getTargetType(),
                store != null ? store.getAccount().getAccountId() : null,
                writer.getNickname(),
                saved.getTitle()
        ));

        return InquiryResponseDto.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<InquiryResponseDto> getMyInquiries(Long accountId, Pageable pageable) {
        return inquiryRepository.findByWriter_AccountId(accountId, pageable)
                .map(InquiryResponseDto::from);
    }

    @Transactional(readOnly = true)
    public InquiryDetailResponseDto getMyInquiryDetail(Long accountId, Long inquiryId) {
        Inquiry inquiry = inquiryRepository.findByInquiryId(inquiryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INQUIRY_NOT_FOUND));

        if (!inquiry.isOwnedBy(accountId)) {
            throw new ForbiddenException(ErrorCode.INQUIRY_ACCESS_DENIED);
        }

        List<InquiryAnswerResponseDto> answers = getAnswerDtos(inquiryId);
        return InquiryDetailResponseDto.of(inquiry, answers);
    }

    // ===================== 패키지 내부 공유 유틸 =====================

    List<InquiryAnswerResponseDto> getAnswerDtos(Long inquiryId) {
        List<InquiryAnswer> answers =
                inquiryAnswerRepository.findByInquiry_InquiryIdOrderByCreatedAtAsc(inquiryId);
        return answers.stream().map(InquiryAnswerResponseDto::from).collect(Collectors.toList());
    }
}
