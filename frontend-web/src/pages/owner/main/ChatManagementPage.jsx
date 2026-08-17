import React, {
  useState,
  useRef,
  useEffect,
  useCallback,
  useLayoutEffect,
} from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { chatApi } from '../../../api/owner/chatApi';
import useChatSocket from '../../../hooks/useChatSocket';
import ImageUploaderGrid from '../../../components/common/ImageUploaderGrid';
import ChatMessageItem from '../../../components/owner/chat/ChatMessageItem';

const PageContainer = styled.div`
  flex: 1;
  padding: 20px;
  background-color: #f8f9fa;
  height: calc(100vh - 160px);
  display: flex;
  flex-direction: column;
  font-family: 'Noto Sans KR', sans-serif;
`;

const ChatWrapper = styled.div`
  flex: 1;
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #eaeaea;
  position: relative;
`;

const ChatHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 24px;
  border-bottom: 1px solid #eaeaea;
  position: relative; /* 팝업 기준점 */
  background-color: #ffffff;
  border-bottom: 1px solid #eaeaea;
  position: relative; /* 팝업 기준점 */
`;

const UserProfile = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;

const Avatar = styled.div`
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background-color: #42a574;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  overflow: hidden;
`;

const UserInfo = styled.div`
  display: flex;
  flex-direction: column;
`;

const TitleRow = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const UserName = styled.span`
  font-size: 16px;
  font-weight: bold;
  color: #333;
`;

const ParticipantCountBadge = styled.button`
  background: #edf2f7;
  border: none;
  border-radius: 12px;
  padding: 2px 8px;
  font-size: 12px;
  color: #4a5568;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: background 0.2s;
  &:hover {
    background: #e2e8f0;
  }
`;

const ShopBadge = styled.span`
  font-size: 12px;
  color: #888;
  margin-top: 3px;
`;

const StatusIndicator = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: ${({ $connected }) => ($connected ? '#42a574' : '#e03131')};
  font-weight: 500;
  &::before {
    content: '';
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background-color: ${({ $connected }) =>
      $connected ? '#42a574' : '#e03131'};
  }
`;

/* 참여자 목록 드롭다운 팝업 */
const ParticipantsDropdown = styled.div`
  position: absolute;
  top: 70px;
  left: 24px;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
  z-index: 100;
  width: 220px;
  padding: 12px;
`;

const DropdownTitle = styled.div`
  font-size: 12px;
  font-weight: bold;
  color: #a0aec0;
  margin-bottom: 8px;
  padding-left: 4px;
`;

const ParticipantList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
`;

const ParticipantItem = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px;
`;

const MiniAvatar = styled.img`
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
  background: #e2e8f0;
`;

const ParticipantName = styled.span`
  font-size: 14px;
  color: #2d3748;
  font-weight: 500;
`;

const MeTag = styled.span`
  font-size: 10px;
  color: #42a574;
  background: #e6f6ec;
  padding: 1px 4px;
  border-radius: 4px;
  margin-left: auto;
`;

/* 나머지 레이아웃 스타일 컴포넌트 생략 (이전과 동일) */
const MessageArea = styled.div`
  flex: 1;
  padding: 24px;
  background-color: #f8f9fa;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
`;
const InputBarContainer = styled.div`
  padding: 20px 24px;
  background-color: #ffffff;
  border-top: 1px solid #eaeaea;
`;
const InputFieldWrapper = styled.div`
  display: flex;
  align-items: center;
  background-color: #ffffff;
  border: 1px solid #e0e0e0;
  border-radius: 28px;
  padding: 8px 10px 8px 14px;
  &:focus-within {
    border-color: #42a574;
    box-shadow: 0 0 0 1px #42a574;
  }
`;
const PlusButton = styled.button`
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: #f1f3f5;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 400;
  color: #666;
  margin-right: 10px;
  &:hover {
    background-color: #e9ecef;
  }
`;
const MessageInput = styled.input`
  flex: 1;
  border: none;
  outline: none;
  font-size: 15px;
  color: #333;
`;
const SendIconButton = styled.button`
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: #f1f3f5;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #42a574;
`;
const ContextMenu = styled.div`
  position: absolute;
  top: ${({ $y }) => `${$y}px`};
  left: ${({ $x }) => `${$x}px`};
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 2000;
  padding: 4px;
  width: 110px;
`;
const MenuButton = styled.button`
  width: 100%;
  background: none;
  border: none;
  padding: 8px 12px;
  text-align: left;
  font-size: 13px;
  color: #e03131;
  font-weight: 500;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  &:hover {
    background-color: #fff5f5;
  }
`;
const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
`;
const ModalContent = styled.div`
  background: white;
  padding: 24px;
  border-radius: 16px;
  width: 400px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
`;
const ModalTitle = styled.h3`
  margin: 0;
  font-size: 16px;
  color: #333;
`;
const ModalActionRow = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
`;
const ConfirmButton = styled.button`
  background: ${({ $isDelete }) => ($isDelete ? '#e03131' : '#00a651')};
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  &:disabled {
    background: #cbd5e1;
  transition: all 0.2s;

  &:hover {
    background-color: #fff5f5;
  }
`;
const CancelButton = styled.button`
  background: #f1f3f5;
  border: none;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
`;
const LeaveButton = styled.button`
  padding: 6px 12px;
  background-color: #fff1f0;
  color: #e03131;
  border: 1px solid #ffc9c9;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background-color: #ffe3e3;
  }
`;

export default function ShopChatManagement() {
  const roomKey = sessionStorage.getItem('storeChatRoom_id');
  const ROOM_ID = parseInt(roomKey, 10);
  const navigate = useNavigate();

  // 채팅방 세부 정보 상태 (createdBy를 accountId로 저장)
  const [roomInfo, setRoomInfo] = useState({
    name: '실시간 고객 문의 상담방',
    participantCount: 0,
    participants: [],
    accountId: 0,
  });

  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [inputValue, setInputValue] = useState('');
  const [showParticipants, setShowParticipants] = useState(false);

  const [uploadImages, setUploadImages] = useState([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [contextMenu, setContextMenu] = useState(null);
  const [selectedMessageId, setSelectedMessageId] = useState(null);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);

  const scrollRef = useRef(null);
  const chatWrapperRef = useRef(null);

  // 소켓 수신 메시지 가공 및 중복 방지
  const handleIncomingMessage = useCallback((newMsg) => {
    setRoomInfo((prevRoom) => {
      const isMyMessage =
        Number(newMsg.createdBy ?? newMsg.senderAccountId) ===
        Number(prevRoom.accountId);

      setMessages((prev) => {
        // 이미 목록에 존재하는 메시지라면 추가하지 않음
        const isAlreadyExist = prev.some(
          (msg) => msg.messageId && msg.messageId === newMsg.messageId,
        );
        if (isAlreadyExist) return prev;

        return [
          ...prev,
          {
            ...newMsg,
            isMe: isMyMessage,
            isDeleted: newMsg.deleted || false,
            unreadCount: newMsg.unreadCount || 0,
          },
        ];
      });
      return prevRoom;
    });
  }, []);

  const { connected, sendMessage } = useChatSocket(
    ROOM_ID,
    handleIncomingMessage,
    roomInfo.accountId,
  );

  // 데이터 로드 및 읽음 처리
  const initChat = async () => {
    try {
      setLoading(true);

      await chatApi.markRoomAsRead(ROOM_ID);

      // 1) 방 정보 먼저 수신 후 createdBy 추출
      const roomRes = await chatApi.getRoomDetail(ROOM_ID);
      let roomOwnerId = 0;

      if (roomRes.data.success) {
        const roomData = roomRes.data.data;
        roomOwnerId = roomData.createdBy;

        setRoomInfo({
          name: roomData.name,
          participantCount: roomData.participantCount,
          participants: roomData.participants || [],
          accountId: roomData.createdBy,
        });
      }

      // 2) 메시지 가져온 뒤 createdBy 비교하여 isMe 지정
      const msgRes = await chatApi.getMessages(ROOM_ID);

      if (msgRes.data.success && msgRes.data.data.content) {
        const formatted = [...msgRes.data.data.content]
          .reverse()
          .map((msg) => ({
            ...msg,
            isMe:
              Number(msg.createdBy ?? msg.senderAccountId) ===
              Number(roomOwnerId),
            isDeleted: msg.deleted || false,
            unreadCount: msg.unreadCount || 0,
          }));
        setMessages(formatted);
      }
    } catch (error) {
      console.error('채팅 초기화 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    initChat();
  }, [ROOM_ID]);

  // 바깥쪽 클릭 이벤트 처리
  useEffect(() => {
    const handleOutsideClick = () => {
      setContextMenu(null);
      setShowParticipants(false);
    };
    window.addEventListener('click', handleOutsideClick);
    return () => window.removeEventListener('click', handleOutsideClick);
  }, []);

  useLayoutEffect(() => {
    if (messages.length === 0) return;
    scrollRef.current?.scrollIntoView({
      behavior: loading || connected ? 'auto' : 'smooth',
    });
  }, [messages, loading, connected]);

  const handleSend = () => {
    if (!inputValue || !inputValue.trim()) return;
    if (sendMessage(inputValue.trim())) setInputValue('');
  };

  const handleContextMenu = (e, msg) => {
    if (!msg.isMe || msg.isDeleted) return;
    e.preventDefault();
    if (!chatWrapperRef.current) return;
    const rect = chatWrapperRef.current.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const clickY = e.clientY - rect.top;
    setContextMenu({
      x: clickX + 110 > rect.width ? clickX - 110 : clickX,
      y: clickY,
    });
    setSelectedMessageId(msg.messageId);
  };

  const handleConfirmDelete = async () => {
    if (!selectedMessageId) return;
    try {
      await chatApi.deleteMessage(selectedMessageId);
      await initChat();
      setIsDeleteModalOpen(false);
    } catch (error) {
      console.error(error);
      alert('메시지 삭제에 실패했습니다.');
    }
  };

  const handleImagesSubmit = async () => {
    if (uploadImages.length === 0) return;
    setIsUploading(true);
    try {
      for (const imgObj of uploadImages) {
        if (imgObj.file) {
          const localBlobUrl = URL.createObjectURL(imgObj.file);
          await chatApi.sendImageMessage(ROOM_ID, {
            imageUrl: localBlobUrl,
            clientMessageId: `img-${Date.now()}-${Math.random().toString(36).substr(2, 4)}`,
          });
        }
      }
      await initChat();
      setUploadImages([]);
      setIsModalOpen(false);
    } catch (error) {
      console.error(error);
    } finally {
      setIsUploading(false);
    }
  };

  const handleCloseRoom = async () => {
    if (!window.confirm('정말로 이 채팅방을 나가시겠습니까?')) return;

    try {
      await chatApi.closeRoom(ROOM_ID);
      alert('채팅방에서 나갔습니다.');

      sessionStorage.setItem('storeChatRoomCreated', 'false');
      sessionStorage.removeItem('storeChatRoom_id');

      navigate('/inquiry');
      window.location.reload();
    } catch (error) {
      console.error('채팅방 나가기 실패:', error);
      alert('채팅방 나가기에 실패했습니다.');
    }
  };

  return (
    <PageContainer>
      <ChatWrapper ref={chatWrapperRef}>
        <ChatHeader onClick={(e) => e.stopPropagation()}>
          <UserProfile>
            <Avatar>문의</Avatar>
            <UserInfo>
              <TitleRow>
                <UserName>{roomInfo.name}</UserName>
                <ParticipantCountBadge
                  onClick={() => setShowParticipants(!showParticipants)}
                >
                  👥 {roomInfo.participantCount}
                </ParticipantCountBadge>
              </TitleRow>
              <ShopBadge>🏠 맛있는 반찬가게 · 마포구</ShopBadge>
            </UserInfo>
          </UserProfile>
          <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
            <StatusIndicator $connected={connected}>
              {connected ? '실시간 연결됨' : '연결 끊김'}
            </StatusIndicator>
            <LeaveButton onClick={handleCloseRoom}>방 나가기</LeaveButton>
          </div>
          {showParticipants && (
            <ParticipantsDropdown>
              <DropdownTitle>
                대화 상대 ({roomInfo.participantCount})
              </DropdownTitle>
              <ParticipantList>
                {roomInfo.participants.map((user) => (
                  <ParticipantItem key={user.accountId}>
                    <MiniAvatar
                      src={user.profileImageUrl || '/default-profile.png'}
                      alt={user.name}
                    />
                    <ParticipantName>
                      {user.name} ({user.nickname})
                    </ParticipantName>
                    {user.accountId === roomInfo.accountId && <MeTag>나</MeTag>}
                  </ParticipantItem>
                ))}
              </ParticipantList>
            </ParticipantsDropdown>
          )}
        </ChatHeader>

        <MessageArea>
          {loading ? (
            <div style={{ textAlign: 'center', color: '#888' }}>
              채팅 내역 로드 중...
            </div>
          ) : (
            messages.map((msg, index) => (
              <ChatMessageItem
                key={`msg-${msg.messageId || index}`}
                msg={msg}
                onContextMenu={handleContextMenu}
              />
            ))
          )}
          <div ref={scrollRef} />
        </MessageArea>

        {contextMenu && (
          <ContextMenu
            $x={contextMenu.x}
            $y={contextMenu.y}
          >
            <MenuButton
              onClick={() => {
                setIsDeleteModalOpen(true);
                setContextMenu(null);
              }}
            >
              🗑️ 삭제하기
            </MenuButton>
          </ContextMenu>
        )}

        <InputBarContainer>
          <InputFieldWrapper>
            <PlusButton
              type="button"
              onClick={() => setIsModalOpen(true)}
            >
              +
            </PlusButton>
            <MessageInput
              placeholder="고객에게 보낼 메시지를 입력하세요..."
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.nativeEvent.isComposing)
                  handleSend();
              }}
            />
            <SendIconButton
              onClick={handleSend}
              disabled={!inputValue.trim()}
            >
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.5"
              >
                <line
                  x1="22"
                  y1="2"
                  x2="11"
                  y2="13"
                ></line>
                <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
              </svg>
            </SendIconButton>
          </InputFieldWrapper>
        </InputBarContainer>
      </ChatWrapper>

      {/* 모달 영역 */}
      {isModalOpen && (
        <ModalOverlay onClick={() => !isUploading && setIsModalOpen(false)}>
          <ModalContent onClick={(e) => e.stopPropagation()}>
            <ModalTitle>전송할 이미지 선택 (최대 5장)</ModalTitle>
            <ImageUploaderGrid
              variant="product"
              images={uploadImages}
              onChange={setUploadImages}
              maxCount={5}
            />
            <ModalActionRow>
              <CancelButton
                disabled={isUploading}
                onClick={() => setIsModalOpen(false)}
              >
                취소
              </CancelButton>
              <ConfirmButton
                disabled={isUploading || uploadImages.length === 0}
                onClick={handleImagesSubmit}
              >
                {isUploading
                  ? '전송 중...'
                  : `${uploadImages.length}장의 사진 전송`}
              </ConfirmButton>
            </ModalActionRow>
          </ModalContent>
        </ModalOverlay>
      )}

      {isDeleteModalOpen && (
        <ModalOverlay onClick={() => setIsDeleteModalOpen(false)}>
          <ModalContent onClick={(e) => e.stopPropagation()}>
            <ModalTitle>🚨 메시지 삭제</ModalTitle>
            <div style={{ fontSize: '14px', color: '#555', lineHeight: '1.5' }}>
              정말로 이 메시지를 삭제하시겠습니까?
              <br />
              삭제된 대화는 복구할 수 없으며 대화창 전체에 반영됩니다.
            </div>
            <ModalActionRow>
              <CancelButton onClick={() => setIsDeleteModalOpen(false)}>
                취소
              </CancelButton>
              <ConfirmButton
                $isDelete={true}
                onClick={handleConfirmDelete}
              >
                정말 삭제
              </ConfirmButton>
            </ModalActionRow>
          </ModalContent>
        </ModalOverlay>
      )}
    </PageContainer>
  );
}
