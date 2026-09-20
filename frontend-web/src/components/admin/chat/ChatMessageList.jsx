import styled from 'styled-components';
import { Trash2, Image as ImageIcon } from 'lucide-react';

const List = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const MessageRow = styled.div`
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;

  &:hover {
    background: #fafafa;
  }
`;

const Avatar = styled.div`
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e8ebee;
  color: #a0a6b5;
  flex-shrink: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
`;

const Body = styled.div`
  flex: 1;
  min-width: 0;
`;

const Header = styled.div`
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 4px;

  .name {
    font-size: 13px;
    font-weight: 700;
    color: #262626;
  }
  .time {
    font-size: 11px;
    color: #bfbfbf;
  }
`;

const Content = styled.div`
  font-size: 13px;
  color: ${(props) => (props.$deleted ? '#bfbfbf' : '#262626')};
  font-style: ${(props) => (props.$deleted ? 'italic' : 'normal')};
  word-break: break-word;
  white-space: pre-wrap;
`;

const MessageImage = styled.img`
  max-width: 220px;
  border-radius: 8px;
  margin-top: 6px;
`;

const NoImagePlaceholder = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f5f5f5;
  color: #8c8c8c;
  font-size: 12px;
  width: fit-content;
`;

const DeleteButton = styled.button`
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: 6px;
  border: 1px solid #ffccc7;
  background: white;
  color: #cf1322;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  align-self: center;

  &:hover {
    background: #fff1f0;
  }
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 40px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

const formatTime = (value) =>
  value ? new Date(value).toLocaleString('ko-KR') : '-';

function ChatMessageList({ messages, loading, onDeleteMessage }) {
  if (loading) return <EmptyText>불러오는 중...</EmptyText>;
  if (messages.length === 0)
    return <EmptyText>주고받은 메시지가 없어요.</EmptyText>;

  return (
    <List>
      {messages.map((msg) => (
        <MessageRow key={msg.messageId}>
          <Avatar>
            {msg.senderProfileImageUrl ? (
              <img
                src={msg.senderProfileImageUrl}
                alt={msg.senderName}
              />
            ) : (
              msg.senderName?.charAt(0)
            )}
          </Avatar>
          <Body>
            <Header>
              <span className="name">{msg.senderName}</span>
              <span className="time">{formatTime(msg.sentAt)}</span>
            </Header>
            <Content $deleted={msg.deleted}>{msg.content}</Content>
            {!msg.deleted &&
              msg.messageType === 'IMAGE' &&
              (msg.imageUrl ? (
                <MessageImage
                  src={msg.imageUrl}
                  alt="첨부 이미지"
                />
              ) : (
                <NoImagePlaceholder>
                  <ImageIcon size={14} />
                  이미지
                </NoImagePlaceholder>
              ))}
          </Body>
          {!msg.deleted && msg.messageType !== 'SYSTEM' && (
            <DeleteButton onClick={() => onDeleteMessage(msg.messageId)}>
              <Trash2 size={12} />
              삭제
            </DeleteButton>
          )}
        </MessageRow>
      ))}
    </List>
  );
}

export default ChatMessageList;
