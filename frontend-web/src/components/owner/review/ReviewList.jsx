import React from 'react';
import styled from 'styled-components';
import { Inbox } from 'lucide-react';
import ReviewItem from './ReviewItem'; // 분리된 ReviewItem 임포트

// --- Styled Components (최상단 위치) ---
const ListContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
`;

const EmptyStateContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border-radius: 16px;
  padding: 60px 24px;
  border: 1px solid #f3f4f6;
  text-align: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
`;

const EmptyIconWrapper = styled.div`
  width: 56px;
  height: 56px;
  background-color: #f9fafb;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  margin-bottom: 16px;
`;

const EmptyTitle = styled.h3`
  font-size: 15px;
  font-weight: 600;
  color: #374151;
  margin: 0 0 6px 0;
`;

const EmptySubtitle = styled.p`
  font-size: 13px;
  color: #9ca3af;
  margin: 0;
`;

export default function ReviewList({
  reviews,
  onCreateReply,
  onUpdateReply,
  onDeleteReply,
  onOpenReport,
}) {
  // 리뷰 목록이 비어있는 상태 대응
  if (!reviews || reviews.length === 0) {
    return (
      <EmptyStateContainer>
        <EmptyIconWrapper>
          <Inbox size={28} />
        </EmptyIconWrapper>
        <EmptyTitle>아직 등록된 리뷰가 없습니다</EmptyTitle>
        <EmptySubtitle>
          고객들이 남긴 소중한 후기가 여기에 표시됩니다.
        </EmptySubtitle>
      </EmptyStateContainer>
    );
  }

  return (
    <ListContainer>
      {reviews.map((review) => (
        <ReviewItem
          key={review.storereviewId} // 콘솔 고유 Key 에러 해결
          review={review}
          onCreateReply={onCreateReply}
          onUpdateReply={onUpdateReply}
          onDeleteReply={onDeleteReply}
          onOpenReport={onOpenReport}
        />
      ))}
    </ListContainer>
  );
}
