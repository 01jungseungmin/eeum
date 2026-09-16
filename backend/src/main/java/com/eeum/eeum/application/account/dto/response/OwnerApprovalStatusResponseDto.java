package com.eeum.eeum.application.account.dto.response;

import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OwnerApprovalStatusResponseDto {

    private AccountRole role;
    private boolean ownerInfoExists;
    private ApprovalStatus approvalStatus;
    private boolean canAccessApprovalPage;
}
