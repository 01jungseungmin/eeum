import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import {
  CHAT_ROOM_TYPE_LABEL,
  CHAT_ROOM_REF_TYPE_LABEL,
} from '../../../constants/chatConstants';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
`;

const TableWrapper = styled.div`
  overflow-x: auto;
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  th {
    text-align: left;
    padding: 10px 12px;
    color: #8c8c8c;
    font-weight: 600;
    border-bottom: 1px solid #f0f0f0;
    white-space: nowrap;
  }
  td {
    padding: 12px;
    border-bottom: 1px solid #f5f5f5;
    color: #262626;
    white-space: nowrap;
  }
  tr:last-child td {
    border-bottom: none;
  }
`;

const Row = styled.tr`
  cursor: pointer;
  &:hover td {
    background: #fafafa;
  }
`;

const NameCell = styled.div`
  max-width: 220px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const PreviewCell = styled.div`
  max-width: 260px;
  color: #595959;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const TypeTag = styled.span`
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: #f0f5ff;
  color: #2f54eb;
`;

const StatusPill = styled.span`
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: ${(props) => (props.$active ? '#edf5f1' : '#f5f5f5')};
  color: ${(props) => (props.$active ? '#2d5a43' : '#8c8c8c')};
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 40px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

const formatDate = (value) =>
  value ? new Date(value).toLocaleString('ko-KR') : '-';

function ChatRoomTable({ rooms, loading }) {
  const navigate = useNavigate();

  return (
    <Card>
      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : rooms.length === 0 ? (
        <EmptyText>조건에 맞는 채팅방이 없어요.</EmptyText>
      ) : (
        <TableWrapper>
          <Table>
            <thead>
              <tr>
                <th>방 이름</th>
                <th>타입</th>
                <th>연관 도메인</th>
                <th>참여자</th>
                <th>안읽음</th>
                <th>마지막 메시지</th>
                <th>마지막 활동</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {rooms.map((room) => (
                <Row
                  key={room.roomId}
                  onClick={() =>
                    navigate(`/admin/chat/rooms/${room.roomId}`, {
                      state: { room },
                    })
                  }
                >
                  <td>
                    <NameCell>{room.name || `방 #${room.roomId}`}</NameCell>
                  </td>
                  <td>
                    <TypeTag>
                      {CHAT_ROOM_TYPE_LABEL[room.type] || room.type}
                    </TypeTag>
                  </td>
                  <td>
                    {CHAT_ROOM_REF_TYPE_LABEL[room.refType] || room.refType}
                  </td>
                  <td>{room.participantCount ?? 0}</td>
                  <td>{room.unreadCount ?? 0}</td>
                  <td>
                    <PreviewCell>
                      {room.lastMessagePreview || '-'}
                    </PreviewCell>
                  </td>
                  <td>{formatDate(room.lastMessageAt)}</td>
                  <td>
                    <StatusPill $active={room.active}>
                      {room.active ? '활성' : '비활성'}
                    </StatusPill>
                  </td>
                </Row>
              ))}
            </tbody>
          </Table>
        </TableWrapper>
      )}
    </Card>
  );
}

export default ChatRoomTable;
