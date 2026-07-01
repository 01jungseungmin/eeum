import React from 'react';
import styled from 'styled-components';
import { Star, MessageSquare, CheckCircle, Percent } from 'lucide-react';

// --- Styled Components (최상단 위치) ---
const StatsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
  width: 100%;
`;

const StatCard = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 20px;
  border: 1px solid #f3f4f6;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  gap: 16px;
`;

const IconBox = styled.div`
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: ${(props) => props.$bg || '#f3f4f6'};
  color: ${(props) => props.$color || '#9ca3af'};
`;

const StatInfo = styled.div`
  display: flex;
  flex-direction: column;
`;

const StatLabel = styled.span`
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
  margin-bottom: 4px;
`;

const StatValue = styled.span`
  font-size: 20px;
  font-weight: 700;
  color: #111827;
`;

export default function ReviewStats({ reviews }) {
  const totalReviews = reviews.length;

  // 1. 평균 평점 실시간 수식 연동
  const averageRating =
    totalReviews > 0
      ? (
          reviews.reduce((acc, curr) => acc + curr.rating, 0) / totalReviews
        ).toFixed(1)
      : '0.0';

  // 2. 5점 만점 리뷰 개수 수식 연동
  const fiveStarCount = reviews.filter((r) => Number(r.rating) === 5).length;

  // 3. 사장님 답글 완료 개수 및 답변율 계산
  const answeredCount = reviews.filter((r) => r.reply).length;
  const replyRate =
    totalReviews > 0 ? Math.round((answeredCount / totalReviews) * 100) : 0;

  return (
    <StatsGrid>
      {/* 카드 1: 평균 평점 */}
      <StatCard>
        <IconBox $bg="#fffbeb" $color="#d97706">
          <Star size={22} fill="#d97706" />
        </IconBox>
        <StatInfo>
          <StatLabel>평균 평점</StatLabel>
          <StatValue>{averageRating} / 5.0</StatValue>
        </StatInfo>
      </StatCard>

      {/* 카드 2: 전체 리뷰수 */}
      <StatCard>
        <IconBox $bg="#edf2ff" $color="#4f46e5">
          <MessageSquare size={22} />
        </IconBox>
        <StatInfo>
          <StatLabel>전체 리뷰 수</StatLabel>
          <StatValue>{totalReviews}건</StatValue>
        </StatInfo>
      </StatCard>

      {/* 카드 3: 만점 리뷰 비율 */}
      <StatCard>
        <IconBox $bg="#f0fdf4" $color="#16a34a">
          <CheckCircle size={22} />
        </IconBox>
        <StatInfo>
          <StatLabel>5점 만점 리뷰</StatLabel>
          <StatValue>{fiveStarCount}건</StatValue>
        </StatInfo>
      </StatCard>

      {/* 카드 4: 답글 작성률 */}
      <StatCard>
        <IconBox $bg="#fdf2f8" $color="#db2777">
          <Percent size={22} />
        </IconBox>
        <StatInfo>
          <StatLabel>답글 작성률</StatLabel>
          <StatValue>
            {replyRate}% ({answeredCount}건 완료)
          </StatValue>
        </StatInfo>
      </StatCard>
    </StatsGrid>
  );
}
