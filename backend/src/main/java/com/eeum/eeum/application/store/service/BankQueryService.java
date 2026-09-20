package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.store.dto.response.BankResponseDto;
import com.eeum.eeum.domain.store.enums.Bank;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 은행 목록의 단일 출처. 프론트가 셀렉트 박스를 이 응답으로 채우면
 * 은행이 추가돼도 화면마다 목록이 갈라지지 않는다.
 */
@Service
public class BankQueryService {

    // 선언 순서가 곧 화면 노출 순서다 - DB 조회가 없어 트랜잭션도 두지 않는다
    public List<BankResponseDto> getBanks() {
        return Arrays.stream(Bank.values())
                .map(BankResponseDto::from)
                .toList();
    }
}
