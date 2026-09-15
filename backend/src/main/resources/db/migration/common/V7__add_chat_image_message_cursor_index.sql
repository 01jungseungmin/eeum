-- 채팅 사진 모아보기: 방 안에서 IMAGE·미삭제 메시지만 sent_at/PK 최신순 커서 조회한다.
CREATE INDEX idx_chat_message_room_image_sent
    ON chat_message (chat_room_id, message_type, deleted_at, sent_at, chat_message_id);
