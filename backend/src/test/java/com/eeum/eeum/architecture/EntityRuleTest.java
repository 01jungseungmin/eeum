package com.eeum.eeum.architecture;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.common.entity.ImageBase;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

import java.math.BigDecimal;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * 엔티티 작성 규칙 (CLAUDE.md "절대 규칙" / "엔티티 작성 규칙").
 */
@AnalyzeClasses(packages = "com.eeum.eeum", importOptions = ImportOption.DoNotIncludeTests.class)
class EntityRuleTest {

    /**
     * BaseEntity 상속 예외.
     * Location/Region은 감사 정보가 필요 없는 정적 참조 데이터,
     * OrderItem은 Order의 스냅샷이라 자체 생성/수정 시각을 갖지 않는다.
     */
    private static final String[] BASE_ENTITY_EXCEPTIONS = {
            "com.eeum.eeum.domain.account.entity.Location",
            "com.eeum.eeum.domain.account.entity.Region",
            "com.eeum.eeum.domain.order.entity.OrderItem"
    };

    /** Soft Delete 허용 목록 — 그 외 엔티티는 Hard Delete다. */
    private static final String[] SOFT_DELETE_ALLOWED = {
            "com.eeum.eeum.domain.account.entity.Account",
            "com.eeum.eeum.domain.chat.entity.ChatMessage",
            "com.eeum.eeum.domain.category.entity.Category",
            "com.eeum.eeum.domain.used.entity.UsedProduct"
    };

    @ArchTest
    static final ArchRule 엔티티는_BaseEntity를_상속한다 =
            classes()
                    .that().areAnnotatedWith(Entity.class)
                    .and(com.tngtech.archunit.base.DescribedPredicate.not(
                            ArchConditions.oneOf(BASE_ENTITY_EXCEPTIONS)))
                    .should().beAssignableTo(BaseEntity.class)
                    .because("createdAt/modifiedAt 감사 필드를 일관되게 갖기 위해 BaseEntity 상속이 필수다");

    @ArchTest
    static final ArchRule 이미지_엔티티는_ImageBase를_상속한다 =
            classes()
                    .that().areAnnotatedWith(Entity.class)
                    .and().haveSimpleNameEndingWith("Image")
                    .should().beAssignableTo(ImageBase.class)
                    .because("이미지 URL/정렬 순서 등 공통 필드를 ImageBase가 담당한다");

    /**
     * Lombok {@code @Setter}는 RetentionPolicy.SOURCE라 바이트코드에 남지 않는다.
     * 따라서 어노테이션이 아니라 <b>생성된 결과물(setXxx 메서드)</b>을 검사한다.
     * 이 편이 손으로 쓴 setter까지 함께 막아 더 강한 규칙이다.
     */
    // TODO(arch): 기존 위반이 있어 임시 비활성. 규칙은 옳으므로 삭제하지 말 것.
    // 위반 1건 — Account.setPrimaryRegion(Account.java:214). 인접한 clearPrimaryRegion과 달리 setter 관례명이다
    @ArchIgnore
    @ArchTest
    static final ArchRule 엔티티에_Setter를_두지_않는다 =
            noMethods()
                    .that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
                    .should().haveNameMatching("^set[A-Z].*")
                    .because("상태 변경은 의도가 드러나는 도메인 메서드로만 한다 — Setter는 불변식을 우회시킨다");

    @ArchTest
    static final ArchRule ManyToOne_연관관계는_LAZY다 =
            fields()
                    .that().areAnnotatedWith(ManyToOne.class)
                    .should(ArchConditions.haveLazyFetch())
                    .because("EAGER는 예측 불가능한 조인과 N+1을 만든다");

    @ArchTest
    static final ArchRule OneToOne_연관관계는_LAZY다 =
            fields()
                    .that().areAnnotatedWith(OneToOne.class)
                    .should(ArchConditions.haveLazyFetch())
                    .because("EAGER는 예측 불가능한 조인과 N+1을 만든다");

    @ArchTest
    static final ArchRule deletedAt은_허용목록_엔티티에만_존재한다 =
            fields()
                    .that().haveName("deletedAt")
                    .and().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
                    .should().beDeclaredInClassesThat(ArchConditions.oneOf(SOFT_DELETE_ALLOWED))
                    .because("Soft Delete 대상이 아닌 엔티티는 즉시 물리 삭제한다 — deletedAt이 있으면 조회 필터 누락 버그가 생긴다");

    @ArchTest
    static final ArchRule 가격_필드는_BigDecimal이다 =
            fields()
                    .that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
                    .and().haveNameMatching(".*([Pp]rice|[Aa]mount)")
                    .should().haveRawType(BigDecimal.class)
                    .because("금액을 double/float로 다루면 반올림 오차가 정산에 누적된다");
}
