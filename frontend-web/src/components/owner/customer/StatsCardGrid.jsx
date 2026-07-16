import React from 'react';
import styled from 'styled-components';
import { User, Heart, Bell, Star, TrendingUp } from 'lucide-react';

const CardGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(1, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
  @media (min-width: 768px) {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
`;
const Card = styled.div`
  background-color: #ffffff;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
`;
const CardTitle = styled.p`
  font-size: 12px;
  color: #94a3b8;
  font-weight: 500;
  margin: 0;
`;
const CardValue = styled.p`
  font-size: 20px;
  font-weight: 700;
  margin: 4px 0 0 0;
  color: #1e293b;
`;
const IconWrapper = styled.div`
  padding: 8px;
  background-color: ${(props) => props.$bg};
  color: ${(props) => props.$color};
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
`;

export default function StatsCardGrid({ stats }) {
  return (
    <CardGrid>
      <Card>
        <div>
          <CardTitle>전체 고객</CardTitle>
          <CardValue>{stats.totalCount}명</CardValue>
        </div>
        {/* 사용처에서도 앞에 $를 붙여줍니다 */}
        <IconWrapper $bg="#e6f4ea" $color="#137333">
          <User size={20} />
        </IconWrapper>
      </Card>
      <Card>
        <div>
          <CardTitle>단골 고객</CardTitle>
          <CardValue>{stats.vipCount}명</CardValue>
        </div>
        <IconWrapper $bg="#fce8e6" $color="#d93025">
          <Heart size={20} fill="currentColor" />
        </IconWrapper>
      </Card>
      <Card>
        <div>
          <CardTitle>즐겨찾기 / 알림</CardTitle>
          <CardValue>
            {stats.favCount} / {stats.alertCount}
          </CardValue>
        </div>
        <IconWrapper $bg="#e8eaf6" $color="#3f51b5">
          <Bell size={20} />
        </IconWrapper>
      </Card>
      <Card>
        <div>
          <CardTitle>이번 달 신규</CardTitle>
          <CardValue>{stats.newCount}명</CardValue>
        </div>
        <IconWrapper $bg="#fef7e0" $color="#b06000">
          <Star size={20} />
        </IconWrapper>
      </Card>
      <Card>
        <div>
          <CardTitle>누적 매출</CardTitle>
          <CardValue>{stats.totalSales}만원</CardValue>
        </div>
        <IconWrapper $bg="#e8f0fe" $color="#1a73e8">
          <TrendingUp size={20} />
        </IconWrapper>
      </Card>
    </CardGrid>
  );
}
