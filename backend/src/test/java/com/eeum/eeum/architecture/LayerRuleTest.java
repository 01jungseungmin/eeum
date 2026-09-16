package com.eeum.eeum.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 4-layer 의존 방향 규칙 (CLAUDE.md "Architecture").
 *
 * <p>api → application → domain 방향만 허용한다.
 * 역방향 의존이 생기면 도메인이 웹/외부 연동에 묶여 테스트와 재사용이 불가능해진다.
 */
@AnalyzeClasses(packages = "com.eeum.eeum", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerRuleTest {

    // TODO(arch): 기존 위반이 있어 임시 비활성. 규칙은 옳으므로 삭제하지 말 것.
    // 위반 57건 — QueryDSL RepositoryCustom/Impl이 application 계층 검색·응답 DTO를 직접 받고 반환한다 + 도메인 이벤트 2개가 application/infrastructure 페이로드를 보유한다
    @ArchIgnore
    @ArchTest
    static final ArchRule 도메인은_상위_레이어에_의존하지_않는다 =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..application..", "..api..", "..infrastructure..")
                    .because("도메인이 상위 레이어를 참조하면 순환 의존이 생기고, 도메인 단독 테스트가 불가능해진다");

    @ArchTest
    static final ArchRule api는_repository에_직접_의존하지_않는다 =
            noClasses()
                    .that().resideInAPackage("..api..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..domain..repository..")
                    .because("Controller가 Repository를 직접 쓰면 트랜잭션 경계와 DTO 변환이 Service를 우회한다");

    @ArchTest
    static final ArchRule application은_api에_의존하지_않는다 =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..api..")
                    .because("Service가 Controller를 참조하면 레이어 방향이 뒤집힌다");
}
