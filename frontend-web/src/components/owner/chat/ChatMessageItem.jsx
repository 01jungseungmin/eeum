import React from 'react';
import styled from 'styled-components';

const MessageRow = styled.div`
  display: flex;
  width: 100%;
  justify-content: ${({ $isMe }) => ($isMe ? 'flex-end' : 'flex-start')};
  align-items: flex-start;
  gap: 8px;
`;

const DeletedMessageRow = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  margin: 14px 0;
`;

const DeletedMessageCenter = styled.div`
  background-color: #e9ecef;
  color: #868e96;
  font-size: 12px;
  padding: 6px 16px;
  border-radius: 16px;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.02);
  font-weight: 500;
`;

const Avatar = styled.div`
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background-color: #42a574;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  overflow: hidden;
  font-size: 13px;
  margin-right: 4px;
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
`;

const BubbleWrap = styled.div`
  display: flex;
  align-items: flex-end;
  gap: 6px;
  max-width: 65%;
  flex-direction: ${({ $isMe }) => ($isMe ? 'row-reverse' : 'row')};
`;

const ChatBubble = styled.div`
  padding: 12px 18px;
  border-radius: ${({ $isMe }) =>
    $isMe ? '18px 18px 4px 18px' : '18px 18px 18px 4px'};
  background-color: ${({ $isMe }) => ($isMe ? '#42a574' : '#ffffff')};
  color: ${({ $isMe }) => ($isMe ? '#ffffff' : '#333333')};
  font-size: 14px;
  line-height: 1.6;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
  white-space: pre-wrap;
  cursor: pointer;
  user-select: none;
`;

const ChatImage = styled.img`
  width: 240px;
  height: 240px;
  border-radius: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
  object-fit: cover;
  display: block;
  cursor: pointer;
`;

const TimeStamp = styled.span`
  font-size: 11px;
  color: #aaa;
  white-space: nowrap;
`;

export default function ChatMessageItem({ msg, onContextMenu }) {
  const isMe = msg.isMe;
  const isDeleted = msg.isDeleted || msg.deleted;

  const formatTime = (isoString) => {
    if (!isoString) return '';
    const date = new Date(isoString);
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  };

  if (isDeleted) {
    return (
      <DeletedMessageRow>
        <DeletedMessageCenter>⚠️ 메시지가 삭제되었습니다.</DeletedMessageCenter>
      </DeletedMessageRow>
    );
  }

  const isImage =
    String(msg.messageType).toUpperCase() === 'IMAGE' || !!msg.imageUrl;

  return (
    <MessageRow $isMe={isMe}>
      {!isMe && (
        <Avatar>
          {msg.senderProfileImageUrl ? (
            <img src={msg.senderProfileImageUrl} alt={msg.senderName} />
          ) : (
            msg.senderName?.charAt(0) || '고'
          )}
        </Avatar>
      )}
      <BubbleWrap $isMe={isMe}>
        {isImage ? (
          <ChatImage
            src={msg.imageUrl}
            alt="전송 이미지"
            onContextMenu={(e) => onContextMenu(e, msg)}
          />
        ) : (
          <ChatBubble $isMe={isMe} onContextMenu={(e) => onContextMenu(e, msg)}>
            {msg.content || '내용 없음'}
          </ChatBubble>
        )}
        <TimeStamp>{formatTime(msg.sentAt)}</TimeStamp>
      </BubbleWrap>
    </MessageRow>
  );
}
