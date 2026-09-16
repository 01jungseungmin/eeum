package com.eeum.eeum.application.account.mapper;
import com.eeum.eeum.application.store.dto.response.SettlementAccountResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreBusinessHourResponseDto;
import com.eeum.eeum.domain.store.entity.SettlementAccount;
import com.eeum.eeum.domain.store.entity.StoreBusinessHour;
import org.springframework.stereotype.Component;

import static com.eeum.eeum.common.util.MaskingUtil.maskAccountNumber;

@Component
public class StoreApprovalMapper {
    // ===================== 영업 시간 응답 변환 =====================
    public StoreBusinessHourResponseDto toBusinessHourDto(StoreBusinessHour businessHour) {
        return StoreBusinessHourResponseDto.builder()
                .dayOfWeek(businessHour.getDayOfWeek())
                .dayLabel(businessHour.getDayOfWeek().getLabel())
                .closed(businessHour.isClosed())
                .openTime(businessHour.getOpenTime())
                .closeTime(businessHour.getCloseTime())
                .build();
    }

    // ===================== 계좌 정보 응답 변환 =====================
    public SettlementAccountResponseDto toSettlementAccountDto(SettlementAccount settlementAccount) {
        return SettlementAccountResponseDto.builder()
                .settlementAccountId(settlementAccount.getSettlementAccountId())
                .bankName(settlementAccount.getBankName())
                .accountNumber(maskAccountNumber(settlementAccount.getAccountNumber()))
                .accountHolder(settlementAccount.getAccountHolder())
                .build();
    }
}
