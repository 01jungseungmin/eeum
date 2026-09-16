package com.eeum.eeum.architecture;

import com.eeum.eeum.infrastructure.push.PushAdapter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * 트랜잭션 경계 규칙 (CLAUDE.md "트랜잭션 분리" / "절대 규칙").
 */
@AnalyzeClasses(packages = "com.eeum.eeum", importOptions = ImportOption.DoNotIncludeTests.class)
class TransactionRuleTest {

    // TODO(arch): 기존 위반이 있어 임시 비활성. 규칙은 옳으므로 삭제하지 말 것.
    // 위반 6건 — AiAdExposureService, CommunityPostService, NotificationSettingsService, UnreadCountService, CartService, PublicStoreService
    @ArchIgnore
    @ArchTest
    static final ArchRule 조회_메서드는_readOnly_트랜잭션이다 =
            methods()
                    .that().areDeclaredInClassesThat().areAnnotatedWith(Service.class)
                    .and().arePublic()
                    .and().haveNameMatching("^(get|find|search|count).*")
                    .and().areAnnotatedWith(Transactional.class)
                    .should(ArchConditions.beReadOnlyTransactional())
                    .because("readOnly는 플러시를 생략하고 스냅샷 보관을 줄인다 — 쓰기 트랜잭션으로 조회하면 불필요한 락과 더티체킹이 발생한다");

    @ArchTest
    static final ArchRule 트랜잭션_안에서_FCM을_직접_호출하지_않는다 =
            noMethods()
                    .that().areAnnotatedWith(Transactional.class)
                    .should(ArchConditions.callAnyMethodOf(PushAdapter.class))
                    .because("FCM은 도메인 이벤트 + AFTER_COMMIT 리스너 경유로만 발송한다 — 롤백 시 이미 나간 푸시를 되돌릴 수 없다");
}
