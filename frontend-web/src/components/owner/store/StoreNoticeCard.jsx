import React, { useState } from 'react';
import styled from 'styled-components';
import { Plus, X } from 'lucide-react';

const Card = styled.div`
  background: white;
  border-radius: 16px;
  border: 1px solid #e8e8e8;
  padding: 20px;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
`;

const QuickBar = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
`;

const QuickBtn = styled.button`
  background: white;
  border: 1px dashed ${(props) => props.$color};
  padding: 12px;
  border-radius: 10px;
  cursor: pointer;
  font-weight: 600;
  color: ${(props) => props.$color};
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
`;

const NoticeList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const NoticeItem = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #f4faf6;
  border: 1px solid #e2f0e7;
  border-radius: 20px;
  font-size: 13px;
  color: #333;
  line-height: 1.5;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #ebf5ee;
    border-color: #cbe3d3;
  }
`;

const NoticeContent = styled.div`
  flex: 1;
  word-break: break-all;
  display: flex;
  align-items: flex-start;
  gap: 6px;
`;

const DeleteButtonWrapper = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  cursor: pointer;
  padding: 4px;
  color: #aaa;
  transition: color 0.2s;

  &:hover {
    color: #ff4d4f;
  }
`;

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
`;

const ModalBox = styled.div`
  background: white;
  width: 440px;
  padding: 24px;
  border-radius: 16px;
`;

const TypeSelectGroup = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin: 16px 0;
`;

const TypeOption = styled.button`
  padding: 16px;
  border-radius: 10px;
  border: 1px solid ${(props) => (props.$active ? '#2d5a43' : '#e8e8e8')};
  background: white;
  cursor: pointer;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
`;

const TextArea = styled.textarea`
  width: 100%;
  height: 100px;
  border: 1px solid #e8e8e8;
  border-radius: 10px;
  padding: 12px;
  box-sizing: border-box;
  resize: none;
  margin-bottom: 16px;
`;

const BtnGroup = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
`;

const ActionBtn = styled.button`
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
  cursor: pointer;
  font-weight: 600;
  background: ${(props) => (props.$primary ? '#2d5a43' : 'white')};
  color: ${(props) => (props.$primary ? 'white' : '#666')};
`;

function StoreNoticeCard({
  notices,
  onAddNotice,
  onDeleteNotice,
  onUpdateNotice,
}) {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState('CREATE');
  const [selectedNoticeId, setSelectedNoticeId] = useState(null);
  const [customType, setCustomType] = useState('NORMAL');
  const [customText, setCustomText] = useState('');

  // 공지 추가 모달 열기
  const openCreateModal = () => {
    setModalMode('CREATE');
    setSelectedNoticeId(null);
    setCustomType('NORMAL');
    setCustomText('');
    setIsModalOpen(true);
  };

  // 공지 클릭 시 수정 모달 열기
  const openEditModal = (notice) => {
    setModalMode('EDIT');
    setSelectedNoticeId(notice.noticeId);
    setCustomType(notice.noticeType || 'NORMAL');
    setCustomText(notice.content);
    setIsModalOpen(true);
  };

  // 모달 최종 저장/수정 처리
  const submitCustomNotice = () => {
    if (!customText.trim()) return alert('공지 내용을 입력하세요.');

    if (modalMode === 'CREATE') {
      onAddNotice(customType, customText);
    } else {
      let noticeTitle = '상점 공지사항';
      if (customType === 'CLOSED_TODAY') noticeTitle = '오늘 휴무 안내';
      else if (customType === 'SOLD_OUT') noticeTitle = '재료 소진 안내';

      onUpdateNotice(selectedNoticeId, {
        title: noticeTitle,
        content: customText,
        noticeType: customType,
        pinned: true,
      });
    }

    setCustomText('');
    setCustomType('NORMAL');
    setIsModalOpen(false);
  };

  const getNoticeEmoji = (type) => {
    if (type === 'CLOSED_TODAY') return '🚪';
    if (type === 'SOLD_OUT') return '🥘';
    return '📢';
  };

  return (
    <Card>
      <Header>
        <h4 style={{ margin: 0, fontSize: '15px' }}>📢 상점 공지 관리</h4>
        <button
          onClick={openCreateModal}
          style={{
            background: 'none',
            border: 'none',
            color: '#2d5a43',
            fontWeight: '600',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '2px',
            fontSize: '13px',
          }}
        >
          <Plus size={14} /> 공지 추가
        </button>
      </Header>

      <QuickBar>
        <QuickBtn
          $color="#ff4d4f"
          onClick={() =>
            onAddNotice(
              'CLOSED_TODAY',
              '오늘 휴무입니다. 내일 정상 영업합니다.',
            )
          }
        >
          🚪 오늘 휴무
        </QuickBtn>
        <QuickBtn
          $color="#faad14"
          onClick={() =>
            onAddNotice(
              'SOLD_OUT',
              '재료 소진으로 오늘 영업이 조기 마감됩니다.',
            )
          }
        >
          🥘 재료 소진
        </QuickBtn>
      </QuickBar>

      <NoticeList>
        {notices.length === 0 ? (
          <div
            style={{
              textAlign: 'center',
              fontSize: '13px',
              color: '#999',
              padding: '20px 0',
            }}
          >
            등록된 상점 공지가 없습니다.
          </div>
        ) : (
          notices.map((n) => (
            <NoticeItem key={n.noticeId} onClick={() => openEditModal(n)}>
              <NoticeContent>
                <span>{getNoticeEmoji(n.noticeType)}</span>
                <span>{n.content}</span>
              </NoticeContent>

              <DeleteButtonWrapper
                onClick={(e) => {
                  e.stopPropagation();
                  onDeleteNotice(n.noticeId);
                }}
              >
                <X size={14} />
              </DeleteButtonWrapper>
            </NoticeItem>
          ))
        )}
      </NoticeList>

      {/* 공통 입력 / 수정 팝업 모달창 */}
      {isModalOpen && (
        <ModalOverlay onClick={() => setIsModalOpen(false)}>
          <ModalBox onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: '0 0 8px 0', fontSize: '16px' }}>
              {modalMode === 'CREATE' ? '공지 추가' : '공지 수정'}
            </h3>
            <div style={{ fontSize: '13px', color: '#888' }}>공지 유형</div>

            <TypeSelectGroup>
              <TypeOption
                $active={customType === 'CLOSED_TODAY'}
                onClick={() => setCustomType('CLOSED_TODAY')}
              >
                🚪 휴무
              </TypeOption>
              <TypeOption
                $active={customType === 'SOLD_OUT'}
                onClick={() => setCustomType('SOLD_OUT')}
              >
                🥘 재료소진
              </TypeOption>
              <TypeOption
                $active={customType === 'NORMAL'}
                onClick={() => setCustomType('NORMAL')}
              >
                📢 직접입력
              </TypeOption>
            </TypeSelectGroup>

            <TextArea
              placeholder="공지 내용을 입력하세요..."
              value={customText}
              onChange={(e) => setCustomText(e.target.value)}
            />

            <BtnGroup>
              <ActionBtn onClick={() => setIsModalOpen(false)}>취소</ActionBtn>
              <ActionBtn $primary onClick={submitCustomNotice}>
                {modalMode === 'CREATE' ? '공지 등록' : '수정 완료'}
              </ActionBtn>
            </BtnGroup>
          </ModalBox>
        </ModalOverlay>
      )}
    </Card>
  );
}

export default StoreNoticeCard;
