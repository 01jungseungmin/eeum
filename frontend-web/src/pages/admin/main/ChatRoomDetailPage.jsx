import { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import styled from 'styled-components';
import { ArrowLeft, Ban } from 'lucide-react';
import ChatMessageList from '../../../components/admin/chat/ChatMessageList';
import { chatApi } from '../../../api/admin/chatApi';
import {
  CHAT_ROOM_TYPE_LABEL,
  CHAT_ROOM_REF_TYPE_LABEL,
} from '../../../constants/chatConstants';

const Container = styled.div`
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
`;

const HeaderLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;

  .btn-back {
    background: white;
    border: 1px solid #d9d9d9;
    border-radius: 6px;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: #595959;
    &:hover {
      background: #f5f5f5;
    }
  }

  .title-side {
    h1 {
      margin: 0 0 6px 0;
      font-size: 22px;
      font-weight: 700;
      color: #262626;
    }
    p {
      margin: 0;
      font-size: 13px;
      color: #8c8c8c;
    }
  }
`;

const DeactivateButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border-radius: 8px;
  border: 1px solid #ffccc7;
  background: #fff1f0;
  color: #cf1322;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;

  &:hover {
    background: #ffe0de;
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
`;

const PaginationContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
`;

const PageButton = styled.button`
  min-width: 32px;
  height: 32px;
  padding: 0 6px;
  border: 1px solid ${(props) => (props.$active ? '#2d5a43' : '#d9d9d9')};
  background: ${(props) => (props.$active ? '#2d5a43' : 'white')};
  color: ${(props) => (props.$active ? 'white' : '#555')};
  font-weight: ${(props) => (props.$active ? '700' : '500')};
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;

  &:hover {
    border-color: #2d5a43;
    color: ${(props) => (props.$active ? 'white' : '#2d5a43')};
  }
  &:disabled {
    background: #f5f5f5;
    color: #ccc;
    border-color: #d9d9d9;
    cursor: not-allowed;
  }
`;

const PAGE_SIZE = 30;

function ChatRoomDetailPage() {
  const { roomId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  // 방 메타 정보(이름/타입/활성여부)를 내려주는 단건 조회 API가 없어서, 목록에서
  // 넘어올 때 location.state로 전달받는다. 직접 URL로 들어오면 room이 없을 수 있다.
  const [room, setRoom] = useState(location.state?.room || null);

  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const fetchMessages = useCallback(async () => {
    setLoading(true);
    try {
      const res = await chatApi.getRoomMessages(roomId, {
        page,
        size: PAGE_SIZE,
      });
      if (res.data?.success) {
        setMessages(res.data.data.content || []);
        setTotalPages(res.data.data.totalPages || 1);
      }
    } catch (error) {
      console.error('채팅 메시지 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [roomId, page]);

  useEffect(() => {
    if (roomId) queueMicrotask(() => fetchMessages());
  }, [roomId, fetchMessages]);

  const handleDeleteMessage = async (messageId) => {
    if (!window.confirm('이 메시지를 강제로 삭제하시겠습니까?')) return;
    try {
      await chatApi.forceDeleteMessage(messageId);
      setMessages((prev) =>
        prev.map((m) =>
          m.messageId === messageId
            ? { ...m, deleted: true, content: '삭제된 메시지입니다.' }
            : m,
        ),
      );
    } catch (error) {
      alert(
        error.response?.data?.error?.message ||
          '메시지 삭제 중 오류가 발생했습니다.',
      );
    }
  };

  const handleDeactivateRoom = async () => {
    if (
      !window.confirm(
        '이 채팅방을 강제로 비활성화하시겠습니까? 되돌릴 수 없습니다.',
      )
    )
      return;
    try {
      await chatApi.forceDeactivateRoom(roomId);
      alert('채팅방이 비활성화되었습니다.');
      setRoom((prev) => (prev ? { ...prev, active: false } : prev));
    } catch (error) {
      alert(
        error.response?.data?.error?.message ||
          '채팅방 비활성화 중 오류가 발생했습니다.',
      );
    }
  };

  return (
    <Container>
      <Header>
        <HeaderLeft>
          <div
            className="btn-back"
            onClick={() => navigate('/admin/chat/rooms')}
          >
            <ArrowLeft size={18} />
          </div>
          <div className="title-side">
            <h1>{room?.name || `채팅방 #${roomId}`}</h1>
            <p>
              {room
                ? `${CHAT_ROOM_TYPE_LABEL[room.type] || room.type} · ${
                    CHAT_ROOM_REF_TYPE_LABEL[room.refType] || room.refType
                  } · 참여자 ${room.participantCount ?? 0}명`
                : '목록에서 진입하면 방 정보가 함께 표시돼요.'}
            </p>
          </div>
        </HeaderLeft>

        <DeactivateButton
          onClick={handleDeactivateRoom}
          disabled={room && !room.active}
        >
          <Ban size={14} />
          {room && !room.active ? '이미 비활성화됨' : '채팅방 강제 비활성화'}
        </DeactivateButton>
      </Header>

      <Card>
        <ChatMessageList
          messages={messages}
          loading={loading}
          onDeleteMessage={handleDeleteMessage}
        />
      </Card>

      {totalPages > 1 && (
        <PaginationContainer>
          <PageButton
            disabled={page === 0}
            onClick={() => setPage((prev) => prev - 1)}
          >
            &lt;
          </PageButton>
          {Array.from({ length: totalPages }, (_, index) => (
            <PageButton
              key={index}
              $active={page === index}
              onClick={() => setPage(index)}
            >
              {index + 1}
            </PageButton>
          ))}
          <PageButton
            disabled={page === totalPages - 1}
            onClick={() => setPage((prev) => prev + 1)}
          >
            &gt;
          </PageButton>
        </PaginationContainer>
      )}
    </Container>
  );
}

export default ChatRoomDetailPage;
