package com.eeum.eeum.application.chat.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.chat.dto.request.GroupChatRoomCreateRequestDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * roomId를 직접 알고 있어도 공개 채팅방의 지역 경계를 우회할 수 없는지 검증한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class ChatRoomRegionAccessIntegrationTest extends IntegrationTestSupport {



    private final ChatRoomService chatRoomService;
    private final AccountRepository accountRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final RegionRepository regionRepository;
    private final StoreRepository storeRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;

    private Long sameRegionAccountId;
    private Long otherRegionAccountId;
    private Long noRegionAccountId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        Region roomRegion = regionRepository.save(
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3000));
        Region otherRegion = regionRepository.save(
                Region.create("2644010100", "부산광역시", "강서구", "대저동", 3000));

        Account owner = accountRepository.save(
                Account.createOwner("region-owner@test.com", "encoded_pw", "점주", "010-1111-1111"));
        Account sameRegion = accountRepository.save(
                Account.createUser("same-region@test.com", "encoded_pw", "동일지역", "same-region", "010-2222-2222"));
        Account otherRegionAccount = accountRepository.save(
                Account.createUser("other-region@test.com", "encoded_pw", "타지역", "other-region", "010-3333-3333"));
        Account noRegion = accountRepository.save(
                Account.createUser("no-region@test.com", "encoded_pw", "미설정", "no-region", "010-4444-4444"));

        assignVerifiedPrimaryRegion(owner, roomRegion);
        assignVerifiedPrimaryRegion(sameRegion, roomRegion);
        assignVerifiedPrimaryRegion(otherRegionAccount, otherRegion);
        sameRegionAccountId = sameRegion.getAccountId();
        otherRegionAccountId = otherRegionAccount.getAccountId();
        noRegionAccountId = noRegion.getAccountId();

        Store store = Store.createForOwnerSignup(owner, "지역 테스트 상점", "서울시", "02-0000-0000");
        store.open();
        Long storeId = storeRepository.save(store).getStoreId();

        GroupChatRoomCreateRequestDto request = new GroupChatRoomCreateRequestDto();
        ReflectionTestUtils.setField(request, "type", ChatRoomType.GROUP);
        ReflectionTestUtils.setField(request, "refType", ChatRoomRefType.STORE);
        ReflectionTestUtils.setField(request, "refId", storeId);
        roomId = chatRoomService.createGroupRoom(owner.getAccountId(), request).getRoomId();
    }

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAll();
        chatParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
        storeRepository.deleteAll();
        accountRegionRepository.deleteAll();
        accountRepository.deleteAll();
        regionRepository.deleteAll();
    }

    @Test
    void 동일한_인증_대표지역의_사용자는_STORE방에_직접_입장한다() {
        // when
        chatRoomService.joinRoom(sameRegionAccountId, roomId);

        // then
        Optional<ChatParticipant> participant = chatParticipantRepository
                .findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, sameRegionAccountId);
        assertThat(participant).isPresent();
        assertThat(participant.orElseThrow().isActive()).isTrue();
    }

    @Test
    void 타지역_사용자는_roomId를_알아도_STORE방에_입장할_수_없다() {
        // when & then
        assertThatThrownBy(() -> chatRoomService.joinRoom(otherRegionAccountId, roomId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        assertThat(chatParticipantRepository
                .findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, otherRegionAccountId))
                .isEmpty();
    }

    @Test
    void 대표지역이_없는_사용자는_roomId를_알아도_입장할_수_없다() {
        // when & then
        assertThatThrownBy(() -> chatRoomService.joinRoom(noRegionAccountId, roomId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        assertThat(chatParticipantRepository
                .findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, noRegionAccountId))
                .isEmpty();
    }

    private void assignVerifiedPrimaryRegion(Account account, Region region) {
        AccountRegion accountRegion = AccountRegion.create(account, region);
        accountRegion.verify();
        accountRegionRepository.saveAndFlush(accountRegion);
        account.setPrimaryRegion(accountRegion.getAccountRegionId());
        accountRepository.saveAndFlush(account);
    }
}
