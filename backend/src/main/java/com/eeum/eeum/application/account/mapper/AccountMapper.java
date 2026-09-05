package com.eeum.eeum.application.account.mapper;

import com.eeum.eeum.application.account.dto.response.AccountDetailResponseDto;
import com.eeum.eeum.application.account.dto.response.AccountResponseDto;
import com.eeum.eeum.application.account.dto.response.MyPageResponseDto;
import com.eeum.eeum.common.util.MaskingUtil;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AccountMapper {

    private final AccountRegionMapper accountRegionMapper;
    private final OwnerApplicationMapper ownerApplicationMapper;

    // ===================== 계정 응답 변환 =====================
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

    // ===================== 마이페이지 응답 변환 =====================
    public MyPageResponseDto toMyPageResponseDto(Account account, List<AccountRegion> regions) {
        return MyPageResponseDto.builder()
                .accountId(account.getAccountId())
                .email(MaskingUtil.maskEmail(account.getEmail()))
                .nickname(account.getNickname())
                .name(MaskingUtil.maskName(account.getName()))
                .phone(account.getPhone())
                .profileImageUrl(account.getProfileImageUrl())
                .role(account.getRole().name())
                .status(account.getStatus().name())
                .provider(account.getProvider().name())
                .primaryRegionId(account.getPrimaryRegionId())
                .regions(accountRegionMapper.toRegionDtos(regions, account))
                .createdAt(account.getCreatedAt())
                .build();
    }

    // ===================== 계정 상세 정보 응답 변환 =====================
    public AccountDetailResponseDto toAccountDetailResponseDto(
            Account account,
            List<AccountRegion> regions,
            OwnerInfo ownerInfo
    ) {
        return AccountDetailResponseDto.builder()
                .accountId(account.getAccountId())
                .email(account.getEmail())
                .nickname(account.getNickname())
                .name(account.getName())
                .profileImageUrl(account.getProfileImageUrl())
                .role(account.getRole().name())
                .status(account.getStatus().name())
                .provider(account.getProvider().name())
                .emailVerified(account.isEmailVerified())
                .regions(accountRegionMapper.toRegionDtos(regions, account))
                .ownerInfo(ownerInfo != null ? ownerApplicationMapper.toOwnerApplicationDetailResponseDto(ownerInfo) : null)
                .createdAt(account.getCreatedAt())
                .deletedAt(account.getDeletedAt())
                .build();
    }

}