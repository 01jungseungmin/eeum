package com.eeum.eeum.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;

/**
 * 이음 아키텍처 규칙 전용 커스텀 조건.
 *
 * <p>ArchUnit 기본 문법으로 표현할 수 없는 규칙만 여기에 둔다.
 * 기본 문법으로 되는 규칙을 여기로 옮기지 않는다 — 읽기 어려워진다.
 */
final class ArchConditions {

    private ArchConditions() {
    }

    /** JPA 연관관계가 EAGER면 위반. */
    static ArchCondition<JavaField> haveLazyFetch() {
        return new ArchCondition<>("fetch = FetchType.LAZY 여야 한다") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                FetchType fetch = null;
                if (field.isAnnotatedWith(ManyToOne.class)) {
                    fetch = field.getAnnotationOfType(ManyToOne.class).fetch();
                } else if (field.isAnnotatedWith(OneToOne.class)) {
                    fetch = field.getAnnotationOfType(OneToOne.class).fetch();
                }
                if (fetch == FetchType.EAGER) {
                    events.add(SimpleConditionEvent.violated(
                            field, field.getFullName() + " 가 EAGER fetch를 사용한다"));
                }
            }
        };
    }

    /** 지정한 클래스 목록에서만 선언되어야 한다. */
    static DescribedPredicate<JavaClass> oneOf(String... fullyQualifiedNames) {
        Set<String> allowed = Set.copyOf(Arrays.asList(fullyQualifiedNames));
        return DescribedPredicate.describe(
                "허용 목록 " + allowed,
                javaClass -> allowed.contains(javaClass.getFullName()));
    }

    /** 대상 메서드가 지정 타입에 속하고 이름이 일치하는 호출인지. */
    static DescribedPredicate<JavaMethodCall> callTo(Class<?> owner, String methodName) {
        return DescribedPredicate.describe(
                owner.getSimpleName() + "." + methodName + "(..) 호출",
                call -> call.getTargetOwner().isAssignableTo(owner)
                        && call.getTarget().getName().equals(methodName));
    }

    /** 해당 메서드가 지정 타입의 지정 메서드를 호출하는지. */
    static DescribedPredicate<JavaMethod> callMethodOf(Class<?> owner, String methodName) {
        return DescribedPredicate.describe(
                owner.getSimpleName() + "." + methodName + "(..) 를 호출하는 메서드",
                method -> method.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> call.getTargetOwner().isAssignableTo(owner)
                                && call.getTarget().getName().equals(methodName)));
    }

    /** 지정 타입의 어떤 메서드든 호출하면 위반. */
    static ArchCondition<JavaMethod> callAnyMethodOf(Class<?>... owners) {
        String description = Arrays.stream(owners).map(Class::getSimpleName).toList().toString();
        return new ArchCondition<>(description + " 의 메서드를 호출") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                    for (Class<?> owner : owners) {
                        if (call.getTargetOwner().isAssignableTo(owner)) {
                            events.add(SimpleConditionEvent.satisfied(method,
                                    method.getFullName() + " 가 " + call.getTarget().getFullName() + " 를 호출한다"));
                            return;
                        }
                    }
                }
            }
        };
    }

    /** @Transactional 이 readOnly = true 여야 한다. */
    static ArchCondition<JavaMethod> beReadOnlyTransactional() {
        return new ArchCondition<>("@Transactional(readOnly = true) 여야 한다") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Transactional tx = method.getAnnotationOfType(Transactional.class);
                if (!tx.readOnly()) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " 는 조회 메서드인데 readOnly = false 다"));
                }
            }
        };
    }

    /** @Bean 이름(메서드명 또는 name 속성)이 금지어와 일치하면 위반. */
    static ArchCondition<JavaMethod> notDeclareBeanNamed(String forbiddenName) {
        return new ArchCondition<>("이름이 '" + forbiddenName + "' 인 빈을 선언하지 않아야 한다") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (!method.isAnnotatedWith(Bean.class)) {
                    return;
                }
                Bean bean = method.getAnnotationOfType(Bean.class);
                boolean named = method.getName().equals(forbiddenName)
                        || Arrays.asList(bean.name()).contains(forbiddenName)
                        || Arrays.asList(bean.value()).contains(forbiddenName);
                if (named) {
                    events.add(SimpleConditionEvent.violated(
                            method, method.getFullName() + " 가 '" + forbiddenName + "' 빈을 선언한다"));
                }
            }
        };
    }

    /**
     * REQUIRES_NEW 메서드가 @Transactional 메서드에서 <b>직접</b> 호출되면 위반.
     *
     * <p>직접 호출만 검사한다. 람다/콜백을 거친 간접 호출은 바이트코드상 호출자가
     * 합성 메서드(lambda$…)라 어노테이션이 없으므로 이 조건으로는 잡히지 않는다.
     */
    static ArchCondition<JavaMethod> notBeCalledFromTransactionalMethod() {
        return new ArchCondition<>("@Transactional 메서드에서 직접 호출되지 않아야 한다") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (!isRequiresNew(method)) {
                    return;
                }
                for (JavaMethodCall call : method.getCallsOfSelf()) {
                    if (!(call.getOrigin() instanceof JavaMethod caller)) {
                        continue;
                    }
                    if (caller.isAnnotatedWith(Transactional.class)
                            && !isRequiresNew(caller)) {
                        events.add(SimpleConditionEvent.violated(method,
                                caller.getFullName() + " (@Transactional) 가 REQUIRES_NEW 메서드 "
                                        + method.getFullName() + " 를 직접 호출한다 — 커넥션 2개 점유"));
                    }
                }
            }
        };
    }

    private static boolean isRequiresNew(JavaMethod method) {
        return method.isAnnotatedWith(Transactional.class)
                && method.getAnnotationOfType(Transactional.class).propagation() == Propagation.REQUIRES_NEW;
    }
}
