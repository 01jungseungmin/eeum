package com.eeum.eeum.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Controller / API 계약 규칙 (CLAUDE.md "절대 규칙").
 */
@AnalyzeClasses(packages = "com.eeum.eeum", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiRuleTest {

    @ArchTest
    static final ArchRule controller는_엔티티를_반환하지_않는다 =
            noMethods()
                    .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                    .and().arePublic()
                    .should().haveRawReturnType(resideInAPackage("..domain..entity.."))
                    .because("엔티티를 그대로 노출하면 LAZY 프록시 직렬화와 민감 정보 유출이 발생한다 — Service에서 DTO로 변환한다");

    @ArchTest
    static final ArchRule 필드_주입을_사용하지_않는다 =
            noFields()
                    .should().beAnnotatedWith(Autowired.class)
                    .because("의존성 주입은 @RequiredArgsConstructor 생성자 주입으로 통일한다");

    @ArchTest
    static final ArchRule 표준출력으로_로깅하지_않는다 =
            noClasses()
                    .should().accessField(System.class, "out")
                    .orShould().accessField(System.class, "err")
                    .because("로깅은 SLF4J로 통일한다 — System.out.println 금지");
}
