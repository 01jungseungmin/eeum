package com.eeum.eeum.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.eeum.eeum.common.scheduling.InstanceLocalSchedule;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * 스케줄러 중복 실행 방지 규칙.
 *
 * <p>{@code @Scheduled}는 JVM마다 독립적으로 돈다. 인스턴스가 2대면 모든 스케줄러가
 * 두 대에서 동시에 시작하고, 무중단 배포 중에도 구·신 인스턴스가 겹치는 동안 같은 일이 벌어진다.
 * 새 스케줄러를 추가할 때 잠금을 빼먹으면 배포 후에야 이중 처리로 드러나므로 빌드에서 막는다.
 *
 * <p>예외는 {@link InstanceLocalSchedule}을 붙인 경우다. WebSocket 세션처럼 JVM 안에만 있는
 * 자원을 다루는 스케줄은 잠금을 걸면 한 대만 돌아 나머지 인스턴스의 자원이 방치된다 —
 * 이중 처리가 아니라 미처리가 문제가 되는 쪽이다.
 */
@AnalyzeClasses(packages = "com.eeum.eeum", importOptions = ImportOption.DoNotIncludeTests.class)
class SchedulerRuleTest {

    @ArchTest
    static final ArchRule 스케줄러는_분산_잠금을_건다 =
            methods()
                    .that().areAnnotatedWith(Scheduled.class)
                    .and().areNotAnnotatedWith(InstanceLocalSchedule.class)
                    .should().beAnnotatedWith(SchedulerLock.class)
                    .because("여러 인스턴스에서 같은 스케줄이 동시에 실행되면 이중 처리된다 "
                            + "(SchedulerLockConfig 참고)");
}
