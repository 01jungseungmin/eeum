---
description: Mock 단위 테스트로 못 잡는 영속성/DB 제약 시나리오를 Testcontainers로 검증
allowed-tools: Read, Write, Grep, Glob, Bash
---

$ARGUMENTS 클래스(또는 도메인)의 통합 테스트를 작성하세요.

## 목적

`test-gen`이 만드는 단위 테스트는 Repository를 전부 Mock 처리하므로,
Hibernate flush/clear 시점, FK 제약, 실제 쿼리 결과처럼 **실제 DB 없이는 검증할 수 없는 버그**를
구조적으로 잡지 못한다. 이 커맨드는 그 공백을 메우기 위한 것이다.
단위 테스트가 이미 다루는 분기/예외 케이스는 다루지 않는다 — 그건 `test-gen` 영역이다.

## 통합 테스트가 필요한 신호 (대상 선정 기준)

대상 Service/Repository를 읽고 아래 패턴이 있는지 확인한다. 하나라도 있으면 통합 테스트 후보다.

1. **같은 트랜잭션 안에서 엔티티 변경 + `@Modifying` bulk 쿼리가 섞여 있다**
   - 예: `entity.update(...)` 호출 후 같은 메서드에서 `@Modifying(clearAutomatically=true)` 쿼리를 호출
   - 검증 시나리오: API 호출 후 같은 트랜잭션이 끝난 다음 **재조회해서 실제 값이 바뀌었는지** 확인 (응답 DTO만 보면 속는다)
2. **자기참조 FK 또는 부모-자식 구조를 가진 엔티티의 bulk delete/hard delete**
   - 예: 대댓글(`parentCommentId`)이 있는 댓글, 하위 카테고리가 있는 카테고리
   - 검증 시나리오: 자식이 존재하는 상태에서 부모 삭제 시 FK 제약 위반 없이 삭제되는지
3. **여러 계정의 데이터가 섞여야 정상 동작하는 정책**
   - 예: 같은 지역(Region) 인증된 다른 계정의 글이 목록에 보여야 하는 경우, 소유자가 아닌 계정의 접근 차단
   - 검증 시나리오: 계정 A가 쓴 글이 같은 조건을 만족하는 계정 B에게도 보이는지 (또는 안 보여야 하는 경우 차단되는지) 실제 데이터로 확인
4. **LAZY 연관관계를 트랜잭션 경계를 넘어 접근할 가능성이 있는 흐름**
   - 검증 시나리오: 실제 세션/트랜잭션 경계에서 `LazyInitializationException` 없이 끝까지 동작하는지
5. **동시성 방어(분산 락, Pessimistic Lock, Unique 제약)가 적용된 쓰기 연산**
   - 이미 `EventProductConcurrencyIntegrationTest`처럼 별도 동시성 통합 테스트가 있다면 중복 생성하지 않는다.

위 신호가 전혀 없는 단순 CRUD/조회 메서드는 통합 테스트를 만들지 않는다 — 단위 테스트로 충분하다고 보고한다.

## 작업 절차

1. 대상 Service 클래스를 읽고, 위 5가지 신호에 해당하는 메서드를 추린다.
2. 같은 패키지의 기존 단위 테스트(`test-gen`으로 생성된 파일)를 확인해 중복 시나리오를 만들지 않는다.
3. `src/test/java/com/eeum/eeum/application/product/service/EventProductConcurrencyIntegrationTest.java`를 컨벤션 참고용으로 읽는다.
4. 신호별로 시나리오를 도출하고, 아래 컨벤션으로 테스트를 작성한다.
5. Docker가 없는 환경에서도 빌드가 깨지지 않도록 `@EnabledIfDockerAvailable`을 반드시 붙인다.

## 작성 컨벤션 (기존 통합 테스트와 동일하게)

```java
@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class XxxIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("eeum")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    // Redis를 안 쓰는 도메인이면 redis Container와 관련 설정은 생성하지 않는다.

    private final XxxService xxxService;
    private final XxxRepository xxxRepository;
    // 테스트에 필요한 Repository만 주입한다

    @BeforeEach
    void setUp() {
        // 실제 repository.save(...)로 선행 데이터 생성 — Mock 금지
    }

    @AfterEach
    void tearDown() {
        // FK 자식 → 부모 순서로 deleteAll()
    }

    @Test
    void 댓글_수정_시_DB에_반영된다() {
        // when: Service 메서드를 실제로 호출
        // then: 같은 Repository로 재조회해서 실제 DB 값을 assertThat으로 검증
        //       (Service 반환값만 검증하면 영속성 컨텍스트 문제를 못 잡으므로 반드시 재조회한다)
    }
}
```

## 검증 원칙 (가장 중요)

- **Service 메서드의 반환값(DTO)만 검증하지 않는다.** 메모리상 엔티티는 맞는데 DB는 stale한 경우(영속성 컨텍스트 clear 문제)를 잡는 게 이 테스트의 목적이므로, 반드시 같은 트랜잭션이 끝난 뒤 **Repository로 재조회**해서 실제 컬럼 값을 검증한다.
- bulk delete가 섞인 메서드는 삭제 후 자식 엔티티가 남아있는지, FK 위반 없이 끝났는지 둘 다 확인한다.
- 여러 계정이 등장하는 시나리오는 실제로 2개 이상의 `Account`를 `accountRepository.save(...)`로 만들어서 검증한다 — 한 계정만으로는 "본인 글만 보임" 같은 버그를 재현할 수 없다.
- `@Transactional`을 테스트 클래스에 걸어서 자동 롤백시키지 않는다 — 그러면 flush/clear 타이밍 버그가 가려진다. `@AfterEach`에서 명시적으로 정리한다.

## 데이터 생성 규칙

- 테스트 데이터는 실제 정적 팩토리 메서드(`Account.createUser(...)`, `CommunityPost.create(...)` 등)로 생성한다.
- Mock을 쓰지 않는다 — 통합 테스트의 의미가 실제 Hibernate/DB 동작 검증이므로 Mock은 금지한다.
- 외부 API(PortOne, FCM, 카카오, NTS)는 호출 경로에 들어오면 안 되는 시나리오만 선택한다. 외부 API 의존이 강제되는 메서드는 대상에서 제외하고 보고한다.

## 금지 사항

- 테스트를 통과시키기 위해 프로덕션 로직을 약화시키지 않는다.
- H2 등 다른 인메모리 DB로 대체하지 않는다 — MySQL 특유의 FK/제약 동작을 검증하는 게 목적이므로 반드시 Testcontainers MySQL을 사용한다.
- 기존 단위 테스트(`test-gen` 산출물)와 중복되는 단순 분기/예외 케이스는 만들지 않는다.
- Secret, API Key, 환경변수 값을 생성하거나 하드코딩하지 않는다.

## 출력 형식

1. 통합 테스트 대상으로 선정한 메서드와 선정 이유 (위 5가지 신호 중 어떤 것에 해당하는지)
2. 생성/수정한 테스트 파일 경로
3. 작성한 시나리오 목록
4. 대상에서 제외한 메서드와 제외 이유 (단순 CRUD라 단위 테스트로 충분한 경우 등)
5. Docker 미설치 환경에서 실행 시 건너뛰어지는지 확인 결과
