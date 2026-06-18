import React from 'react';
import styled from 'styled-components';
import { Edit2, Trash2 } from 'lucide-react';

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
  }
`;

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

// 💡 [수정] image_bdf743.png 테마 색상에 완벽하게 일치하도록 변경
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
  const soldCount = evt?.soldCount || 0;
  const eventStock = evt?.eventStock || 0;
  const remainingStock = evt?.remainingStock || 0;

  const salePercent =
    eventStock > 0 ? Math.round((soldCount / eventStock) * 100) : 0;
  const isOut = remainingStock <= 0;

  // 현재 시간과 시작/종료 시간을 비교하여 동적으로 상태 판별
  const now = new Date();
  const startAt = evt?.startAt ? new Date(evt?.startAt) : null;
  const endAt = evt?.endAt ? new Date(evt?.endAt) : null;

  let currentStatus = 'DONE'; // 기본값 종료
  let statusText = '종료';

  if (isOut) {
    // 매진이면 무조건 종료 상태
    currentStatus = 'DONE';
    statusText = '종료';
  } else if (startAt && endAt) {
    if (now < startAt) {
      // 현재 시간이 시작 시간 전이면 진행 예정
      currentStatus = 'READY';
      statusText = '진행 예정';
    } else if (now >= startAt && now <= endAt) {
      // 현재 시간이 이벤트 기간 사이면 진행중
      currentStatus = 'LIVE';
      statusText = '진행중';
    } else {
      // 기간이 지났으면 종료
      currentStatus = 'DONE';
      statusText = '종료';
    }
  }

  const currentId = evt?.eventProductId || evt?.id;

  return (
    <EventItemCard>
      <ProductImg>🥩</ProductImg>

      <EventInfoContent>
        <div className="badge-row">
          <StatusBadge type={currentStatus}>{statusText}</StatusBadge>
          {currentStatus === 'LIVE' && <LivePulseBadge>⚡ LIVE</LivePulseBadge>}
        </div>

        <h3 className="title">{evt?.productName || '이름 없는 상품'}</h3>

        <div className="price-row">
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
        <button onClick={() => onDelete(currentId)} title="삭제">
          <Trash2 size={16} />
        </button>
      </RightActionGroup>
    </EventItemCard>
  );
}

export default EventItemRow;
