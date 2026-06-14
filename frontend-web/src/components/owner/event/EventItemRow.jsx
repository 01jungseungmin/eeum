// src/components/owner/event/EventItemRow.jsx
import React from 'react';
import styled from 'styled-components';
import { Edit2, Trash2, Clock } from 'lucide-react';

const EventItemCard = styled.div`
  border: 1px solid #eef0f2;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  gap: 16px;
  position: relative;
  background: #fff;
  transition: transform 0.2s;
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
  }
`;

const ProductImg = styled.div`
  width: 72px;
  height: 72px;
  border-radius: 14px;
  background: #f8f9fa;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 32px;
`;

const EventInfoContent = styled.div`
  flex: 1;
  .badge-row {
    display: flex;
    gap: 6px;
    margin-bottom: 8px;
  }
  .title {
    font-size: 15px;
    font-weight: bold;
    color: #1a1f2c;
    margin: 0 0 10px 0;
  }
  .price-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
    .original {
      font-size: 13px;
      color: #cbd5e1;
      text-decoration: line-through;
    }
    .discount {
      font-size: 16px;
      font-weight: bold;
      color: #e52e59;
    }
    .rate-tag {
      background: #e52e59;
      color: white;
      font-size: 11px;
      font-weight: bold;
      padding: 1px 5px;
      border-radius: 4px;
    }
    .time {
      font-size: 12px;
      color: #8e94a0;
      margin-left: 8px;
      display: flex;
      align-items: center;
      gap: 4px;
    }
  }
`;

// 💡 [추가] 판매 현황과 잔여 개수를 한 줄에 배치하기 위한 Flex 컨테이너
const StatusTextRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #8e94a0;
  margin-bottom: 8px;

  .remain-text {
    font-weight: bold;
    color: ${(props) => (props.$isOut ? '#e52e59' : '#00a651')};
  }
`;

const StatusBadge = styled.span`
  font-size: 11px;
  font-weight: bold;
  padding: 3px 8px;
  border-radius: 20px;
  background: ${(props) =>
    props.type === 'LIVE'
      ? '#e6f4ea'
      : props.type === 'READY'
        ? '#e8eefc'
        : '#f1f3f5'};
  color: ${(props) =>
    props.type === 'LIVE'
      ? '#00a651'
      : props.type === 'READY'
        ? '#1a73e8'
        : '#8e94a0'};
`;

const LivePulseBadge = styled.span`
  font-size: 11px;
  font-weight: bold;
  padding: 3px 8px;
  border-radius: 4px;
  background: #e52e59;
  color: white;
  display: flex;
  align-items: center;
  gap: 2px;
`;

const ProgressBarContainer = styled.div`
  width: 100%;
  height: 8px;
  background: #f1f3f5;
  border-radius: 4px;
  overflow: hidden;
  position: relative;
  margin-top: 4px;
`;

const ProgressFill = styled.div`
  height: 100%;
  width: ${(props) => props.$percent}%;
  background: ${(props) => (props.$isFull ? '#e52e59' : '#f59e0b')};
`;

// 💡 [수정] 잔여 개수가 빠져서 상단 수정/삭제 버튼만 우측 상단에 정렬하도록 가볍게 변경
const RightActionGroup = styled.div`
  position: absolute;
  right: 20px;
  top: 20px;
  display: flex;
  gap: 12px;

  button {
    background: none;
    border: none;
    cursor: pointer;
    color: #cbd5e1;
    transition: color 0.2s;
    &:hover {
      color: #8e94a0;
    }
  }
`;

function EventItemRow({ evt, onEdit, onDelete }) {
  // 💡 데이터 방어 처리: 백엔드 값이 없을 경우를 대비해 0이나 기본값 처리
  const soldCount = evt?.soldCount || 0;
  const eventStock = evt?.eventStock || 0;
  const remainingStock = evt?.remainingStock || 0;

  const salePercent =
    eventStock > 0 ? Math.round((soldCount / eventStock) * 100) : 0;
  const isOut = remainingStock <= 0;
  const isLive = evt?.eventStatus === 'ONGOING';

  // 💡 혹시 모를 고유 ID 누락 방어 (key 또는 알림용)
  const currentId = evt?.eventProductId || evt?.id;

  return (
    <EventItemCard>
      <ProductImg>🥩</ProductImg>

      <EventInfoContent>
        <div className="badge-row">
          {isLive && (
            <>
              <StatusBadge type="LIVE">진행중</StatusBadge>
              <LivePulseBadge>⚡ LIVE</LivePulseBadge>
            </>
          )}
          {!isLive && <StatusBadge type="DONE">종료/비활성</StatusBadge>}
        </div>

        {/* 💡 이름이 비어있을 경우 대체 텍스트 */}
        <h3 className="title">{evt?.productName || '이름 없는 상품'}</h3>

        <div className="price-row">
          {/* 💡 [에러 해결] ?를 붙여 undefined 일 때 .toLocaleString()이 실행되어 터지는 현상 전면 차단 */}
          <span className="original">
            {(evt?.originalPrice ?? 0).toLocaleString()}원
          </span>
          <span className="discount">
            {(evt?.eventPrice ?? 0).toLocaleString()}원
          </span>
          <span className="rate-tag">-{evt?.discountRate || 0}%</span>
        </div>

        <StatusTextRow $isOut={isOut}>
          <div>
            판매 현황: {soldCount}/{eventStock}개
          </div>
          <div className="remain-text">
            {isOut ? '매진!' : `잔여 ${remainingStock}개`}
          </div>
        </StatusTextRow>

        <ProgressBarContainer>
          <ProgressFill $percent={salePercent} $isFull={isOut} />
        </ProgressBarContainer>
      </EventInfoContent>

      <RightActionGroup>
        <button onClick={() => onEdit(evt)} title="수정">
          <Edit2 size={16} />
        </button>
        {/* 💡 안전하게 잡힌 ID 값 사용 */}
        <button
          onClick={() => onDelete(currentId, evt?.productName)}
          title="삭제"
        >
          <Trash2 size={16} />
        </button>
      </RightActionGroup>
    </EventItemCard>
  );
}

export default EventItemRow;
