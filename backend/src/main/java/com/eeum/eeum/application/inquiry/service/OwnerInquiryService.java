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
        // 답변은 문의당 1건이다(uk_inquiry_answer_inquiry_id). 상태 검사만으로는
        // 답변이 남은 채 PENDING으로 돌아온 문의를 걸러내지 못한다.
        if (inquiryAnswerRepository.existsByInquiry_InquiryId(inquiryId)) {
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

    /**
     * 사장이 자기 가게 문의에 단 답변의 본문을 정정한다.
     *
     * <p>삭제는 제공하지 않는다 — 관리자 답변과 같은 이유다. 사용자가 알림으로 이미 받아 본
     * 답변이 통째로 사라지면 문의 스레드의 문맥이 끊긴다.
     *
     * <p>관리자 API는 사장 답변을 고칠 수 없으므로(writerType 검사), 이 메서드가 없으면
     * 상점 문의 답변은 오타 하나도 영영 고칠 수 없다.
     *
     * <p>알림은 재발송하지 않는다 — 정정마다 푸시가 나가면 알림 피로를 부른다.
     */
    @Transactional
    public InquiryAnswerResponseDto updateAnswer(
            Long ownerId,
            Long inquiryId,
            Long answerId,
            InquiryAnswerCreateRequestDto request
    ) {
        Long storeId = getOwnerStoreId(ownerId);
        Inquiry inquiry = inquiryRepository.findByInquiryId(inquiryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INQUIRY_NOT_FOUND));

        // 남의 가게 문의를 내 가게 답변인 척 고치는 경로를 막는다.
        if (inquiry.getStore() == null || !inquiry.getStore().getStoreId().equals(storeId)) {
            throw new ForbiddenException(ErrorCode.INQUIRY_ACCESS_DENIED);
        }
        if (inquiry.getTargetType() != InquiryTargetType.STORE) {
            throw new ForbiddenException(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
        }

        InquiryAnswer answer = inquiryAnswerRepository.findById(answerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INQUIRY_ANSWER_NOT_FOUND));

        // 다른 문의의 답변 ID를 넘겨 남의 답변을 고치는 경로를 막는다.
        if (!answer.getInquiry().getInquiryId().equals(inquiry.getInquiryId())) {
            throw new NotFoundException(ErrorCode.INQUIRY_ANSWER_NOT_FOUND);
        }
        // 사장 API로 관리자 답변을 고칠 수 없다.
        if (!answer.isWrittenBy(InquiryAnswerWriterType.OWNER)) {
            throw new ForbiddenException(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
        }

        answer.updateContent(request.getContent());
        return InquiryAnswerResponseDto.from(answer);
    }

    // ===================== 내부 유틸 =====================

    private Long getOwnerStoreId(Long ownerId) {
        return storeRepository.findByAccount_AccountId(ownerId)
                .map(Store::getStoreId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.STORE_NOT_FOUND));
    }
}
