package com.eeum.eeum.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 공통 기반.
 *
 * <p>컨테이너와 Spring 컨텍스트를 <b>테스트 JVM 전체에서 한 벌만</b> 쓴다.
 * 클래스마다 {@code @Container}로 컨테이너를 선언하면 클래스 수만큼 MySQL·Redis가 새로 뜨고,
 * JDBC URL이 매번 달라져 Spring 컨텍스트도 매번 새로 로딩된다.
 * 통합 테스트 클래스가 29개인 이 저장소에서는 그 기동 비용이 실제 테스트 시간보다 훨씬 크다.
 *
 * <p>컨텍스트를 재사용하려면 <b>등록하는 프로퍼티가 모든 클래스에서 같아야</b> 한다.
 * 그래서 SQL 수집기까지 여기서 함께 켠다 — 일부 클래스만 켜면 그 클래스들이 별도 컨텍스트를 쓴다.
 *
 * <p>{@code @EnabledIfDockerAvailable}은 각 구현 클래스에 붙인다 — Testcontainers의 이 조건은
 * 추상 상위 클래스에 두면 평가 시점에 실제 테스트 클래스를 찾지 못해 실패한다.
 * 컨텍스트 캐시 키에는 영향이 없어 공유는 그대로 유지된다.
 *
 * <p><b>주의:</b> DB를 공유하므로 각 테스트는 자기가 만든 데이터를 스스로 정리해야 한다
 * (@AfterEach). 전체 건수를 세는 단언({@code count() == 0})은 다른 클래스가 남긴 데이터에
 * 영향을 받을 수 있으니, 가능하면 자기 픽스처 범위로 좁혀 검증한다.
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public abstract class IntegrationTestSupport {

    // root로 접속한다. 잠금 경쟁 테스트가 performance_schema·information_schema로
    // "실제로 잠금 대기에 들어갔는지"를 확인하는데, 일반 계정에는 그 권한이 없다.
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("eeum")
                    .withUsername("root")
                    .withPassword("test");

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    // 컨테이너 기동은 컨텍스트를 실제로 만들 때만 한다.
    // static 블록에서 띄우면 Docker 없는 환경에서 skip 판정 전에 클래스 로딩이 터진다.
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        startContainers();

        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // 실행 SQL을 스레드별로 수집한다. N+1·쿼리 수 검증에 쓰고, 쓰지 않는 테스트에는 영향이 없다.
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                () -> SqlCaptureInspector.class.getName());
    }

    // 잠금 대기 관측처럼 애플리케이션 커넥션이 아닌 별도 접속이 필요한 테스트용.
    protected static String mysqlJdbcUrl() {
        return MYSQL.getJdbcUrl();
    }

    protected static String mysqlPassword() {
        return MYSQL.getPassword();
    }

    private static synchronized void startContainers() {
        if (!MYSQL.isRunning()) {
            MYSQL.start();
        }
        if (!REDIS.isRunning()) {
            REDIS.start();
        }
        // 컨테이너 정리는 Testcontainers Ryuk이 JVM 종료 시 처리한다.
    }
}
