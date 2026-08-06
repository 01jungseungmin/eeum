package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.dto.request.ChatMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.request.GroupChatRoomCreateRequestDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 가게 단톡방 생성 → 입장 → 종료 → 재생성 전체 흐름 통합 테스트.
 *
 * <p><b>검증 대상</b>
 * <ol>
 *   <li>종료된 방이 남아 있어도 신규 생성이 차단되지 않고 <b>새 roomId</b>가 발급되는지</li>
 *   <li>사용자가 종료된 옛 방이 아니라 현재 ACTIVE 방으로 유입되는지 (목록/상점 노출 기준)</li>
 *   <li>종료된 방에 메시지를 보낼 수 없는지</li>
 *   <li>동시 생성 요청에서 ACTIVE 방이 중복 생성되지 않는지 (Redis 락 + DB 유니크)</li>
 *   <li>MySQL 생성 컬럼 기반 유니크(uk_chat_room_active_ref)가 실제 DDL로 반영되는지</li>
 * </ol>
 */
@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ChatRoomLifecycleIntegrationTest {

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

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final AdminChatService adminChatService;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final NotificationRepository notificationRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final ChatUnreadService chatUnreadService;

    private Long ownerAccountId;
    private Long customerId;
    private Long storeId;

    @BeforeEach
    void setUp() {
        Account owner = accountRepository.save(
                Account.createOwner("owner@test.com", "encoded_pw", "점주", "010-1111-1111"));
        ownerAccountId = owner.getAccountId();

        Account customer = accountRepository.save(
                Account.createUser("cust@test.com", "encoded_pw", "고객", "cust", "010-2222-2222"));
        customerId = customer.getAccountId();

        Store store = Store.createForOwnerSignup(owner, "테스트상점", "서울시", "02-0000-0000");
        store.open();
        storeId = storeRepository.save(store).getStoreId();
    }

    @AfterEach
    void tearDown() {
        // 메시지 발송은 AFTER_COMMIT @Async 리스너가 알림을 생성한다.
        // 그 작업이 끝나기 전에 계정을 지우면 notification FK 위반으로 teardown이 깨지고,
        // 다음 테스트의 setUp이 이메일 중복으로 연쇄 실패한다.
        try {
            Thread.sleep(700);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        chatMessageRepository.deleteAll();
        chatParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
        storeRepository.deleteAll();
        notificationRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ──────────────── 픽스처 ────────────────

    private GroupChatRoomCreateRequestDto storeRoomRequest(List<Long> inviteeIds) {
        GroupChatRoomCreateRequestDto dto = new GroupChatRoomCreateRequestDto();
        ReflectionTestUtils.setField(dto, "type", ChatRoomType.GROUP);
        ReflectionTestUtils.setField(dto, "refType", ChatRoomRefType.STORE);
        ReflectionTestUtils.setField(dto, "refId", storeId);
        ReflectionTestUtils.setField(dto, "participantAccountIds", inviteeIds);
        return dto;
    }

    private ChatMessageSendRequestDto textMessage(String content) {
        ChatMessageSendRequestDto dto = new ChatMessageSendRequestDto();
        ReflectionTestUtils.setField(dto, "content", content);
        return dto;
    }

    // ──────────────── 시나리오 1: 생성 → 입장 → 종료 → 재생성 ────────────────

    @Test
    void 종료된_단톡방이_있어도_동일_조건으로_새_roomId의_방이_생성된다() {
        // given: 사장이 단톡방 개설 + 고객 초대
        Long firstRoomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();
        assertThat(chatParticipantRepository
                .countByChatRoom_ChatroomIdAndStatus(firstRoomId, ParticipantStatus.ACTIVE))
                .isEqualTo(2L);

        // when: 사장이 채팅방 종료(폭파) 후 동일 조건으로 재생성
        chatRoomService.closeRoom(ownerAccountId, firstRoomId);
        Long secondRoomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();

        // then: 옛 방을 재사용하지 않고 새 roomId가 발급된다
        assertThat(secondRoomId).isNotEqualTo(firstRoomId);

        // then: 옛 방은 물리 삭제가 아니라 상태 전이로 보존된다 (대화 기록 유지)
        ChatRoom oldRoom = chatRoomRepository.findById(firstRoomId).orElseThrow();
        assertThat(oldRoom.isActive()).isFalse();
        assertThat(oldRoom.getClosedAt()).isNotNull();
        assertThat(chatMessageRepository
                .findAllByChatRoom_ChatroomIdOrderBySentAtDesc(firstRoomId, PageRequest.of(0, 10))
                .getContent()).isNotEmpty();

        // then: ACTIVE 조회는 항상 새 방만 반환한다
        Optional<ChatRoom> active = chatRoomRepository
                .findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
                        ChatRoomRefType.STORE, storeId);
        assertThat(active).isPresent();
        assertThat(active.get().getChatroomId()).isEqualTo(secondRoomId);
    }

    @Test
    void 종료된_방에는_메시지를_보낼_수_없다() {
        // given
        Long roomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();

        // when
        chatRoomService.closeRoom(ownerAccountId, roomId);

        // then: 사장/고객 모두 발송이 차단된다
        assertThatThrownBy(() -> chatMessageService.sendMessage(ownerAccountId, roomId, textMessage("사장 메시지")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> chatMessageService.sendMessage(customerId, roomId, textMessage("고객 메시지")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 채팅방_목록에는_종료된_방이_제외되고_현재_ACTIVE_방만_노출된다() {
        // given: 첫 방 종료 후 재생성
        Long firstRoomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();
        chatRoomService.closeRoom(ownerAccountId, firstRoomId);
        Long secondRoomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();

        // when
        Slice<ChatRoomResponseDto> ownerRooms =
                chatRoomService.getMyRooms(ownerAccountId, PageRequest.of(0, 20), false);
        Slice<ChatRoomResponseDto> customerRooms =
                chatRoomService.getMyRooms(customerId, PageRequest.of(0, 20), false);

        // then: 종료된 방은 목록에서 사라지고 새 방만 남는다 — 사용자는 옛 방으로 되돌아갈 수 없다
        assertThat(ownerRooms.getContent()).extracting(ChatRoomResponseDto::getRoomId)
                .containsExactly(secondRoomId)
                .doesNotContain(firstRoomId);
        assertThat(customerRooms.getContent()).extracting(ChatRoomResponseDto::getRoomId)
                .containsExactly(secondRoomId);
        assertThat(customerRooms.getContent().get(0).isActive()).isTrue();
    }

    @Test
    void includeClosed로_종료된_방의_지난_대화를_열람할_수_있다() {
        // given: 첫 방 종료 후 재생성
        Long firstRoomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();
        chatRoomService.closeRoom(ownerAccountId, firstRoomId);
        Long secondRoomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();

        // when: 종료된 방까지 포함해 조회
        Slice<ChatRoomResponseDto> withClosed =
                chatRoomService.getMyRooms(customerId, PageRequest.of(0, 20), true);

        // then: 종료 시 참여자를 LEFT로 바꾸지 않으므로 지난 방도 조회된다 — active 값으로 구분 가능
        assertThat(withClosed.getContent()).extracting(ChatRoomResponseDto::getRoomId)
                .containsExactlyInAnyOrder(firstRoomId, secondRoomId);
        assertThat(withClosed.getContent())
                .filteredOn(dto -> dto.getRoomId().equals(firstRoomId))
                .allMatch(dto -> !dto.isActive());
        assertThat(withClosed.getContent())
                .filteredOn(dto -> dto.getRoomId().equals(secondRoomId))
                .allMatch(ChatRoomResponseDto::isActive);

        // then: 종료된 방의 메시지 조회도 여전히 가능해야 한다 (기록 보존의 실효성)
        assertThat(chatMessageService
                .getMessages(customerId, firstRoomId, null, 20)
                .getContent()).isNotEmpty();
    }

    @Test
    void 관리자_강제_종료도_사장_종료와_동일하게_재생성을_막지_않는다() {
        // given
        Long firstRoomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();

        // when: 관리자 경로로 강제 종료 후 사장이 재생성
        adminChatService.forceDeactivateRoom(firstRoomId);
        Long secondRoomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();

        // then
        assertThat(secondRoomId).isNotEqualTo(firstRoomId);
        assertThat(chatRoomRepository.findById(firstRoomId).orElseThrow().getClosedAt()).isNotNull();
        assertThat(chatRoomRepository.findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
                ChatRoomRefType.STORE, storeId).orElseThrow().getChatroomId())
                .isEqualTo(secondRoomId);
    }

    // ──────────────── 시나리오 1-B: 종료 vs 진행 중 쓰기 ────────────────

    @Test
    void 종료_직전에_시작된_메시지_트랜잭션이_종료_상태를_되돌리지_못한다() throws InterruptedException {
        // given: sendMessage는 락을 잡지 않으므로 closeRoom과 겹칠 수 있다.
        //        ChatRoom에 @DynamicUpdate가 없으면 lastMessageAt만 바꿔도 전체 컬럼이 UPDATE되어
        //        커밋 시점에 is_active=1 / closed_at=NULL이 되살아난다 (lost update).
        Long roomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();

        CountDownLatch roomLoaded = new CountDownLatch(1);
        CountDownLatch roomClosed = new CountDownLatch(1);

        Thread writer = new Thread(() -> transactionTemplate.execute(status -> {
            // 종료 이전 스냅샷으로 방을 로드 (sendMessage가 participant.getChatRoom()으로 얻는 것과 동일)
            ChatRoom loaded = chatRoomRepository.findById(roomId).orElseThrow();
            roomLoaded.countDown();
            try {
                roomClosed.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            loaded.updateLastMessageAt(LocalDateTime.now());
            return null;
        }));
        writer.start();

        // when: 메시지 트랜잭션이 방을 든 채로 대기하는 사이 종료가 커밋된다
        assertThat(roomLoaded.await(20, TimeUnit.SECONDS)).isTrue();
        chatRoomService.closeRoom(ownerAccountId, roomId);
        LocalDateTime closedAt = chatRoomRepository.findById(roomId).orElseThrow().getClosedAt();
        roomClosed.countDown();
        writer.join(30_000);

        // then: 종결 상태가 유지되어야 한다
        ChatRoom after = chatRoomRepository.findById(roomId).orElseThrow();
        assertThat(after.isActive())
                .as("종료 이후 커밋된 메시지 트랜잭션이 방을 되살리면 안 된다")
                .isFalse();
        assertThat(after.getClosedAt())
                .as("closed_at이 덮어써지거나 NULL로 되돌아가면 안 된다")
                .isEqualTo(closedAt);
    }

    @Test
    void 마지막_참여자_퇴장과_재생성이_동시에_일어나도_죽은_방이_반환되지_않는다() throws InterruptedException {
        // given: 퇴장의 자동 종료가 생성과 다른 락 키를 쓰면,
        //        생성 쪽이 아직 커밋되지 않은 "곧 종료될 방"을 기존 ACTIVE 방으로 오인해 반환한다.
        Long roomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(null))
                .getRoomId();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Long> returnedRoomId = new AtomicReference<>();
        AtomicReference<Throwable> leaveError = new AtomicReference<>();
        AtomicReference<Throwable> createError = new AtomicReference<>();

        // when: 마지막 참여자(사장) 퇴장 → 방 자동 종료 / 동시에 같은 조건으로 재생성
        Thread leaver = new Thread(() -> {
            try {
                start.await();
                chatRoomService.leaveRoom(ownerAccountId, roomId);
            } catch (Throwable t) {
                leaveError.set(t); // 같은 락을 두고 경합하므로 한쪽 실패는 정상
            } finally {
                done.countDown();
            }
        });
        Thread creator = new Thread(() -> {
            try {
                start.await();
                returnedRoomId.set(chatRoomService
                        .createGroupRoom(ownerAccountId, storeRoomRequest(null)).getRoomId());
            } catch (Throwable t) {
                createError.set(t);
            } finally {
                done.countDown();
            }
        });
        leaver.start();
        creator.start();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

        // then 1: 두 요청이 모두 실패하면 아무것도 검증하지 못한 것이므로 테스트를 실패시킨다
        assertThat(leaveError.get() == null || createError.get() == null)
                .as("퇴장/생성이 모두 실패하면 경합 자체를 검증하지 못한다 (leave=%s, create=%s)",
                        leaveError.get(), createError.get())
                .isTrue();

        // then 2: 생성이 성공했다면 그 방은 반드시 ACTIVE여야 한다
        Long returned = returnedRoomId.get();
        if (returned != null) {
            assertThat(chatRoomRepository.findById(returned).orElseThrow().isActive())
                    .as("생성 API가 종료됐거나 곧 종료될 방의 roomId를 반환하면 안 된다")
                    .isTrue();
        }

        // then 3: 생성이 락 경합으로 실패했더라도, 뒤이은 생성은 반드시 ACTIVE 방을 준다.
        //         (경합 실패로 검증이 비어버리는 것을 막는 확정 검증 구간)
        Long settled = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(null)).getRoomId();
        assertThat(chatRoomRepository.findById(settled).orElseThrow().isActive())
                .as("경합이 끝난 뒤 생성은 항상 ACTIVE 방을 반환해야 한다")
                .isTrue();

        // then 4: 어떤 인터리빙이었든 ACTIVE 가게 단톡방은 최대 1개다
        long activeCount = chatRoomRepository.findAll().stream()
                .filter(r -> ChatRoomRefType.STORE == r.getRefType())
                .filter(r -> storeId.equals(r.getRefId()))
                .filter(ChatRoom::isActive)
                .count();
        assertThat(activeCount)
                .as("동일 가게의 ACTIVE 단톡방은 1개를 넘을 수 없다")
                .isEqualTo(1L);
    }

    @Test
    void 종료와_초대가_동시에_일어나도_종료된_방에_참여자가_남지_않는다() throws InterruptedException {
        // given: 초대가 종료와 다른 락 키를 쓰면, 종료 직전 활성 검증을 통과한 초대가
        //        종료 커밋 이후에 참여자를 남긴다 (그 참여자는 구독·발송이 막혀 아무것도 못 한다).
        Long roomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(null))
                .getRoomId();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> inviteError = new AtomicReference<>();
        AtomicReference<Throwable> closeError = new AtomicReference<>();

        new Thread(() -> {
            try {
                start.await();
                chatRoomService.inviteParticipants(ownerAccountId, roomId, List.of(customerId));
            } catch (Throwable t) {
                inviteError.set(t);
            } finally {
                done.countDown();
            }
        }).start();
        new Thread(() -> {
            try {
                start.await();
                chatRoomService.closeRoom(ownerAccountId, roomId);
            } catch (Throwable t) {
                closeError.set(t);
            } finally {
                done.countDown();
            }
        }).start();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

        assertThat(inviteError.get() == null || closeError.get() == null)
                .as("초대/종료가 모두 실패하면 경합을 검증하지 못한다 (invite=%s, close=%s)",
                        inviteError.get(), closeError.get())
                .isTrue();

        // then: 방이 종료됐다면 그 방에 새 참여자가 추가돼 있으면 안 된다
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        if (!room.isActive()) {
            boolean customerJoined = chatParticipantRepository
                    .findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, customerId)
                    .isPresent();
            assertThat(customerJoined)
                    .as("종료된 방에 참여자가 남으면 그 사용자는 구독·발송이 모두 막힌 방에 갇힌다")
                    .isFalse();
        }
    }

    @Test
    void 동시_종료_요청에도_종료_처리는_정확히_한_번만_수행된다() throws InterruptedException {
        // given: 종료 판정을 엔티티 스냅샷으로 하면 두 요청이 모두 "아직 ACTIVE"로 보고
        //        closed_at 덮어쓰기 + 종료 SYSTEM 메시지/이벤트 중복 발행이 일어난다.
        Long roomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    chatRoomService.closeRoom(ownerAccountId, roomId);
                } catch (Exception ignored) {
                    // 락 경합 실패는 허용
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

        // then: 종료 SYSTEM 메시지가 정확히 1건
        long closeMessages = chatMessageRepository
                .findAllByChatRoom_ChatroomIdOrderBySentAtDesc(roomId, PageRequest.of(0, 50))
                .getContent().stream()
                .filter(m -> "채팅방이 종료되었습니다.".equals(m.getContent()))
                .count();
        assertThat(closeMessages)
                .as("종료 처리가 중복 수행되면 종료 메시지가 2건이 된다")
                .isEqualTo(1L);
        assertThat(chatRoomRepository.findById(roomId).orElseThrow().isActive()).isFalse();
    }

    @Test
    void 동일_참여자_중복_INSERT는_DB_유니크로_차단된다() {
        // given: "조회 후 없으면 INSERT"는 Redis 락이 만료되면 중복 행을 만든다.
        //        중복이 생기면 단건 Optional 조회가 예외를 던져 그 방의 모든 요청이 500이 된다.
        Long roomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        Account customer = accountRepository.findById(customerId).orElseThrow();

        // when & then: 락을 우회해 같은 (방, 계정) 조합을 다시 넣으면 DB가 거부한다
        assertThatThrownBy(() ->
                chatParticipantRepository.saveAndFlush(ChatParticipant.create(room, customer)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // then: 중복이 없으므로 참여자 검증이 계속 정상 동작한다
        assertThat(chatParticipantRepository
                .findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, customerId))
                .isPresent();
    }

    @Test
    void 종료된_방의_unread는_캐시가_사라져도_되살아나지_않는다() throws InterruptedException {
        // given: 고객에게 안 읽은 메시지가 있는 상태에서 방을 종료
        Long roomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();
        chatMessageService.sendMessage(ownerAccountId, roomId, textMessage("안 읽은 메시지"));
        chatRoomService.closeRoom(ownerAccountId, roomId);

        // when: Redis 캐시가 축출/만료된 상황을 모사 (DB fallback 경로로 유도)
        Thread.sleep(700); // AFTER_COMMIT @Async 리스너 정리 대기
        redisTemplate.delete(redisTemplate.keys("unread:chat:*"));

        // then: 종료된 방은 읽어서 회수할 수단이 없으므로 DB 기준값에서도 0이어야 한다
        assertThat(chatMessageService.countUnread(customerId).getUnreadCount())
                .as("종료된 방의 unread가 DB fallback으로 되살아나면 영구히 남는다")
                .isZero();
    }

    @Test
    void 서로_다른_사용자가_같은_clientMessageId를_써도_모두_저장된다() {
        // given: 멱등키에 계정/방 스코프가 없으면 한쪽 메시지가 조용히 유실된다
        Long roomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();

        ChatMessageSendRequestDto ownerMsg = textMessage("사장 메시지");
        ReflectionTestUtils.setField(ownerMsg, "clientMessageId", "seq-1");
        ChatMessageSendRequestDto customerMsg = textMessage("고객 메시지");
        ReflectionTestUtils.setField(customerMsg, "clientMessageId", "seq-1");

        // when
        chatMessageService.sendMessage(ownerAccountId, roomId, ownerMsg);
        chatMessageService.sendMessage(customerId, roomId, customerMsg);

        // then: 두 메시지 모두 저장돼야 한다
        List<String> contents = chatMessageRepository
                .findAllByChatRoom_ChatroomIdOrderBySentAtDesc(roomId, PageRequest.of(0, 50))
                .getContent().stream().map(m -> m.getContent()).toList();
        assertThat(contents).contains("사장 메시지", "고객 메시지");
    }

    @Test
    void 동시_읽음_처리에서_과거_시각이_최신_시각을_덮지_않는다() {
        // given
        Long roomId = chatRoomService
                .createGroupRoom(ownerAccountId, storeRoomRequest(List.of(customerId)))
                .getRoomId();
        var participant = chatParticipantRepository
                .findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, customerId).orElseThrow();

        LocalDateTime newer = LocalDateTime.now().plusMinutes(10);
        LocalDateTime older = LocalDateTime.now().minusMinutes(10);

        // when: 최신 시각을 먼저 반영한 뒤 과거 시각으로 갱신 시도 (@Modifying은 트랜잭션 필요)
        transactionTemplate.execute(st ->
                chatParticipantRepository.advanceLastReadTime(participant.getChatparticipantId(), newer));
        Integer updated = transactionTemplate.execute(st ->
                chatParticipantRepository.advanceLastReadTime(participant.getChatparticipantId(), older));

        // then: 과거 시각은 무시된다
        assertThat(updated).isZero();
        assertThat(chatParticipantRepository.findById(participant.getChatparticipantId())
                .orElseThrow().getLastReadTime())
                .isAfter(LocalDateTime.now());
    }

    @Test
    void unread_보정_CAS는_그_사이_증가한_값을_덮어쓰지_않는다() {
        // given: 스케줄러가 "DB 조회 → SET" 사이에 메시지가 도착하는 상황.
        //        CAS가 아니면 그 증가분이 통째로 사라진다.
        String key = "unread:chat:" + customerId;
        redisTemplate.opsForValue().set(key, "5");

        // when: 보정이 5를 읽은 뒤, SET 직전에 메시지가 도착해 6이 된 상황
        redisTemplate.opsForValue().set(key, "6");
        boolean applied = chatUnreadService.compareAndSetTotal(customerId, 5L, 3L);

        // then: 기대값과 달라졌으므로 덮어쓰지 않는다
        assertThat(applied).isFalse();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("6");

        // and: 값이 그대로면 정상 보정된다
        assertThat(chatUnreadService.compareAndSetTotal(customerId, 6L, 3L)).isTrue();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("3");

        redisTemplate.delete(key);
    }

    @Test
    void unread_보정_CAS는_키가_없을_때만_기대값_null이_성립한다() {
        // given
        String key = "unread:chat:" + customerId;
        redisTemplate.delete(key);

        // when & then: 키가 없으면 null 기대값으로 설정된다
        assertThat(chatUnreadService.compareAndSetTotal(customerId, null, 4L)).isTrue();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("4");

        // and: 이미 값이 있으면 null 기대값은 실패한다 (그 사이 다른 요청이 만든 값 보호)
        assertThat(chatUnreadService.compareAndSetTotal(customerId, null, 9L)).isFalse();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("4");

        redisTemplate.delete(key);
    }

    // ──────────────── 시나리오 2: 동시 생성 ────────────────

    @Test
    void 동일_조건으로_동시에_생성_요청해도_ACTIVE_방은_하나만_만들어진다() throws InterruptedException {
        // given
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        // when: 5개 스레드가 동시에 같은 가게 단톡방 생성을 시도
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    chatRoomService.createGroupRoom(ownerAccountId, storeRoomRequest(null));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();

        // then: 성공/멱등 반환 여부와 무관하게 ACTIVE 방은 정확히 1개여야 한다
        List<ChatRoom> allRooms = chatRoomRepository.findAll();
        long activeCount = allRooms.stream()
                .filter(room -> ChatRoomRefType.STORE == room.getRefType())
                .filter(room -> storeId.equals(room.getRefId()))
                .filter(ChatRoom::isActive)
                .count();
        assertThat(activeCount)
                .as("동시 요청 %d건 중 성공 %d건 / 실패 %d건 — ACTIVE 방은 1개만 남아야 한다",
                        threadCount, successCount.get(), failureCount.get())
                .isEqualTo(1L);
    }

    @Test
    void ACTIVE_단톡방_중복은_DB_유니크_제약으로도_차단된다() {
        // given: 애플리케이션 락을 우회해 리포지토리로 직접 두 번째 ACTIVE 방을 밀어 넣는다
        chatRoomService.createGroupRoom(ownerAccountId, storeRoomRequest(null));
        Account owner = accountRepository.findById(ownerAccountId).orElseThrow();
        ChatRoom duplicate = ChatRoom.createGroup(
                owner, ChatRoomType.GROUP, "중복 단톡방", ChatRoomRefType.STORE, storeId, null);

        // when & then: MySQL 생성 컬럼 + 유니크 인덱스가 두 번째 ACTIVE 방을 거부한다.
        // 예외 "타입"까지 못박아야 한다 — ChatRoomService가 DataIntegrityViolationException만
        // 409로 변환하므로, 다른 타입이 올라오면 운영에서는 409가 아니라 500이 된다.
        assertThatThrownBy(() -> chatRoomRepository.saveAndFlush(duplicate))
                .as("uk_chat_room_active_ref 위반은 DataIntegrityViolationException으로 번역되어야 한다")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 종료된_방은_같은_조합으로_여러_건_누적될_수_있다() {
        // given & when: 생성-종료를 3회 반복 — soft delete 구조와 유니크 제약이 충돌하지 않는지 확인
        for (int i = 0; i < 3; i++) {
            Long roomId = chatRoomService
                    .createGroupRoom(ownerAccountId, storeRoomRequest(null))
                    .getRoomId();
            chatRoomService.closeRoom(ownerAccountId, roomId);
        }

        // then: 종료된 방 3개가 모두 보존되고, ACTIVE 방은 없다
        List<ChatRoom> storeRooms = chatRoomRepository.findAll().stream()
                .filter(room -> storeId.equals(room.getRefId()))
                .toList();
        assertThat(storeRooms).hasSize(3);
        assertThat(storeRooms).allMatch(room -> !room.isActive());
        assertThat(chatRoomRepository.findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
                ChatRoomRefType.STORE, storeId)).isEmpty();
    }
}
