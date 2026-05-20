package com.eeum.eeum.application.account.mapper;

import com.eeum.eeum.application.account.dto.response.AccountRegionResponseDto;
import com.eeum.eeum.application.account.dto.response.AccountResponseDto;
import com.eeum.eeum.application.account.dto.response.OwnerResponseDto;
import com.eeum.eeum.common.util.MaskingUtil;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {
    public AccountResponseDto toAccountResponseDto(Account account) {
        return AccountResponseDto.builder()
                .accountId(account.getAccountId())
                .email(MaskingUtil.maskEmail(account.getEmail()))
                .nickname(account.getNickname())
                .name(MaskingUtil.maskName(account.getName()))
                .profileImageUrl(account.getProfileImageUrl())
                .role(account.getRole().name())
                .status(account.getStatus().name())
                .provider(account.getProvider().name())
                .primaryRegionId(account.getPrimaryRegionId())
                .createdAt(account.getCreatedAt())
                .build();
    }

    public AccountRegionResponseDto toRegionDto(AccountRegion accountRegion, Account account) {
        return AccountRegionResponseDto.builder()
                .accountRegionId(accountRegion.getAccountRegionId())
                .regionId(accountRegion.getRegionId())
                .siDo(accountRegion.getRegion().getSiDo())
                .gunGu(accountRegion.getRegion().getGunGu())
                .dong(accountRegion.getRegion().getDong())
                .isPrimary(accountRegion.getAccountRegionId().equals(account.getPrimaryRegionId()))
                .verified(accountRegion.isVerified())
                .verifiedAt(accountRegion.getVerifiedAt())
                .createdAt(accountRegion.getCreatedAt())
                .build();
    }

    public OwnerResponseDto toOwnerResponseDto(OwnerInfo ownerInfo) {
        return OwnerResponseDto.builder()
                .ownerInfoId(ownerInfo.getOwnerInfoId())
                .accountId(ownerInfo.getAccount().getAccountId())
                .businessNumber(MaskingUtil.maskBusinessNumber(ownerInfo.getBusinessNumber()))
                .approvalStatus(ownerInfo.getApprovalStatus().name())
                .rejectionReason(ownerInfo.getRejectionReason())
                .createdAt(ownerInfo.getCreatedAt())
                .build();
    }
    public OwnerResponseDto toOwnerAdminResponseDto(OwnerInfo ownerInfo) {
        return OwnerResponseDto.builder()
                .ownerInfoId(ownerInfo.getOwnerInfoId())
                .accountId(ownerInfo.getAccount().getAccountId())
                .businessNumber(ownerInfo.getBusinessNumber())
                .approvalStatus(ownerInfo.getApprovalStatus().name())
                .rejectionReason(ownerInfo.getRejectionReason())
                .createdAt(ownerInfo.getCreatedAt())
                .build();
    }
}