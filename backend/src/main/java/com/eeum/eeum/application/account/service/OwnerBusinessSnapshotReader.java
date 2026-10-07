package com.eeum.eeum.application.account.service;

import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class OwnerBusinessSnapshotReader {
    private final OwnerInfoRepository ownerInfoRepository;

    @Transactional(readOnly = true)
    public Snapshot read(Long accountId) {
        OwnerInfo info = ownerInfoRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));
        info.getAccount().assertWritable();
        return new Snapshot(info.getBusinessNumber(), info.getAccount().getName(), info.getOpeningDate());
    }

    public record Snapshot(String businessNumber, String ownerName, LocalDate openingDate) {
        public void assertMatches(OwnerInfo info) {
            if (!businessNumber.equals(info.getBusinessNumber())
                    || !ownerName.equals(info.getAccount().getName())
                    || !openingDate.equals(info.getOpeningDate())) {
                throw new BusinessException(ErrorCode.BUSINESS_VERIFY_FAILED);
            }
        }
    }
}
