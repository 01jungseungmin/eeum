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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * 지정한 테이블에서 행 잠금 대기가 실제로 발생할 때까지 기다린다.
     *
     * <p>경쟁 테스트를 경과 시간으로 판정하면 스레드 스케줄링이 밀렸을 때 잠금이 없어도 통과한다.
     * MySQL이 "이 트랜잭션은 잠금 대기 중"이라고 보고할 때까지 기다린 뒤 경쟁을 진행시켜야 한다.
     *
     * <p>대기 "개수"만 세면 이 테스트와 무관한 트랜잭션(다른 클래스·스케줄러)이 잡혀 경쟁이
     * 재현되지 않았는데도 통과할 수 있으므로, 대기 중인 잠금의 대상 테이블까지 확인한다.
     * performance_schema 조회는 권한이 필요해 컨테이너 root 계정으로 별도 접속한다.
     *
     * @return 제한 시간(15초) 안에 해당 테이블의 잠금 대기를 관측했으면 true
     */
    protected static boolean awaitLockWait(String table) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000L;
        try (Connection connection = DriverManager.getConnection(
                mysqlJdbcUrl(), "root", mysqlPassword())) {
            while (System.currentTimeMillis() < deadline) {
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery(
                             """
                             SELECT COUNT(*)
                             FROM performance_schema.data_lock_waits w
                             JOIN performance_schema.data_locks l
                               ON w.REQUESTING_ENGINE_LOCK_ID = l.ENGINE_LOCK_ID
                             WHERE l.OBJECT_NAME = '%s'
                             """.formatted(table))) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return true;
                    }
                }
                sleepQuietly(100);
            }
        }
        return false;
    }

    /**
     * 비동기 알림이 남긴 행까지 정리한 뒤 계정을 지운다.
     *
     * <p>여러 도메인이 {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code @Async}로
     * 알림을 만든다. 그 INSERT는 테스트 본문이 끝난 뒤에 도착할 수 있고, notification이
     * account를 FK로 참조하므로 한 건이라도 남아 있으면 계정 삭제가 막힌다.
     * 그러면 공유 DB를 쓰는 다음 테스트가 Duplicate entry 같은 엉뚱한 오류로 죽는다.
     */
    protected static void deleteAccountsAbsorbingAsyncNotifications(
            org.springframework.data.jpa.repository.JpaRepository<?, ?> notificationRepository,
            org.springframework.data.jpa.repository.JpaRepository<?, ?> accountRepository
    ) {
        for (int attempt = 0; attempt < 20; attempt++) {
            notificationRepository.deleteAllInBatch();
            try {
                accountRepository.deleteAll();
                return;
            } catch (org.springframework.dao.DataIntegrityViolationException retryable) {
                sleepQuietly(100);
            }
        }
        throw new IllegalStateException("비동기 알림이 계속 도착해 테스트 계정을 정리하지 못했다");
    }

    protected static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 같은 자원을 건드리는 두 쓰기를 실제 행 잠금 위에서 경쟁시킨다.
     *
     * <p>{@code first}는 작업 후 커밋하지 않고 잠금을 쥔 채 대기하고, {@code second}는 그 사이에
     * 같은 경로로 진입한다. MySQL이 {@code lockTable}에서 잠금 대기를 실제로 보고할 때까지
     * 기다린 뒤 첫 트랜잭션을 커밋시킨다 — 경과 시간으로 판정하면 스케줄링이 밀렸을 때
     * 잠금이 없어도 통과한다.
     *
     * <p>잠금이 아예 없는 코드에서는 {@code second}가 대기 없이 지나가므로 여기서 실패한다.
     * 즉 이 헬퍼는 결과 불변식과 별개로 "이 경로가 직렬화된다"는 사실 자체를 고정한다.
     *
     * @param secondFailure {@code second}가 던진 예외를 담아 돌려줄 자리
     */
    protected static void raceOnLock(
            PlatformTransactionManager transactionManager,
            String lockTable,
            Runnable first,
            Runnable second,
            AtomicReference<Throwable> secondFailure
    ) throws Exception {
        CountDownLatch firstDone = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> t1 = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        first.run();
                        firstDone.countDown();
                        awaitQuietly(releaseFirst);
                    }));

            Future<?> t2 = executor.submit(() -> {
                awaitQuietly(firstDone);
                secondStarted.countDown();
                try {
                    second.run();
                } catch (Throwable e) {
                    secondFailure.set(e);
                }
            });

            awaitQuietly(secondStarted);
            if (!awaitLockWait(lockTable)) {
                throw new AssertionError(
                        "두 번째 요청이 " + lockTable + " 행 잠금을 기다리지 않았다 — 경쟁이 재현되지 않음");
            }
            releaseFirst.countDown();

            t1.get(30, TimeUnit.SECONDS);
            t2.get(30, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    protected static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
