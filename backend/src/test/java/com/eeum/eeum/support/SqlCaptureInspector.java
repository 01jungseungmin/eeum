package com.eeum.eeum.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * 현재 스레드에서 실행된 SQL만 모으는 테스트용 Hibernate StatementInspector.
 * <p>
 * 전역 Hibernate Statistics로 쿼리 수를 세면 같은 컨텍스트의 스케줄러(1분 주기 만료 처리 등)가
 * 배경 스레드에서 날리는 쿼리까지 함께 잡혀 N+1 검증이 불규칙하게 실패한다.
 * 검증 대상 호출은 테스트 스레드에서 실행되므로 스레드별로 모으면 배경 쿼리와 분리된다.
 * <p>
 * 사용법: {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}에
 * 이 클래스 이름을 등록한 뒤 {@link #reset()} → 검증 대상 호출 → {@link #captured()} 순으로 사용한다.
 */
public class SqlCaptureInspector implements StatementInspector {

    private static final ThreadLocal<List<String>> CAPTURED = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public String inspect(String sql) {
        CAPTURED.get().add(sql);
        return sql;
    }

    public static void reset() {
        CAPTURED.get().clear();
    }

    public static List<String> captured() {
        return List.copyOf(CAPTURED.get());
    }
}
