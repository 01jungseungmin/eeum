package com.eeum.eeum.domain.store.enums;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 정산 계좌 은행 목록.
 *
 * 표시명은 프론트 셀렉트 값과 같은 표기를 쓴다 - 기존 화면을 고치지 않아도 그대로 통과한다.
 * "국민" · "KB국민"처럼 뒤섞여 들어오는 표기는 별칭으로 받아 대표 이름 하나로 저장한다.
 * 그래야 정산 대사에서 같은 은행이 여러 문자열로 갈라지지 않는다.
 */
public enum Bank {

    // institutionCode(금융결제원 기관코드)는 정산 자동이체를 붙일 때 필요해 자리만 잡아 둔다.
    // 기억으로 채우면 틀린 값이 그대로 굳으므로 PortOne 공식 문서와 대조한 뒤 채운다.
    KB(null, "국민은행", "KB국민", "KB국민은행", "국민"),
    SHINHAN(null, "신한은행", "신한"),
    WOORI(null, "우리은행", "우리"),
    HANA(null, "하나은행", "하나", "KEB하나", "KEB하나은행"),
    IBK(null, "기업은행", "IBK기업", "IBK기업은행", "IBK"),
    NH(null, "농협은행", "NH농협", "NH농협은행", "농협"),
    LOCAL_NH(null, "지역농축협", "농축협", "단위농협"),
    SC(null, "SC제일은행", "SC제일", "제일은행"),
    CITI(null, "한국씨티은행", "한국씨티", "씨티은행", "씨티"),
    KDB(null, "산업은행", "KDB산업", "KDB산업은행"),
    SH(null, "수협은행", "Sh수협", "Sh수협은행", "수협"),
    IM(null, "iM뱅크", "iM뱅크(대구)", "아이엠뱅크", "대구은행", "DGB대구은행"),
    BUSAN(null, "부산은행", "부산"),
    KYONGNAM(null, "경남은행", "경남"),
    GWANGJU(null, "광주은행", "광주"),
    JEONBUK(null, "전북은행", "전북"),
    JEJU(null, "제주은행", "제주"),
    MG(null, "새마을금고", "MG새마을금고", "새마을"),
    SHINHYUP(null, "신협", "신협은행", "신용협동조합"),
    FOREST(null, "산림조합", "산림조합중앙회"),
    SAVINGS_BANK(null, "저축은행", "SB저축은행", "저축은행중앙회"),
    POST(null, "우체국", "우체국예금보험", "우체국은행"),
    KBANK(null, "케이뱅크", "K뱅크"),
    KAKAO(null, "카카오뱅크", "카카오"),
    TOSS(null, "토스뱅크", "토스");

    private static final Map<String, Bank> LOOKUP = new LinkedHashMap<>();

    static {
        for (Bank bank : values()) {
            LOOKUP.put(normalize(bank.name()), bank);
            LOOKUP.put(normalize(bank.displayName), bank);
            for (String alias : bank.aliases) {
                LOOKUP.putIfAbsent(normalize(alias), bank);
            }
        }
    }

    private final String institutionCode;
    private final String displayName;
    private final String[] aliases;

    Bank(String institutionCode, String displayName, String... aliases) {
        this.institutionCode = institutionCode;
        this.displayName = displayName;
        this.aliases = aliases;
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param value 표시명 · 별칭 · enum 상수명 중 무엇이든 받는다
     */
    public static Optional<Bank> find(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOOKUP.get(normalize(value)));
    }

    public static Bank from(String value) {
        return find(value).orElseThrow(() -> new BusinessException(ErrorCode.STORE_BANK_NOT_SUPPORTED));
    }

    /**
     * 저장·비교에 쓸 대표 이름. 별칭으로 들어와도 한 가지 표기로 모인다.
     */
    public static String normalizeName(String value) {
        return from(value).getDisplayName();
    }

    // 공백과 대소문자만 무시한다. 괄호·하이픈까지 지우면 서로 다른 은행이 같은 키로 겹칠 수 있다.
    private static String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
