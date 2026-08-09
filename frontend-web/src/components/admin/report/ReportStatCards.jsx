import React, { useMemo } from 'react';
import styled from 'styled-components';

const StatCardsContainer = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
`;

const StatCard = styled.div`
  background-color: ${(props) => props.color};
  border: 1px solid ${(props) => props.$borderColor};
  border-radius: 12px;
  padding: 20px;
`;

const StatTitle = styled.div`
  font-size: 13px;
  color: #4b5563;
  margin-bottom: 12px;
`;

const StatValue = styled.div`
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 8px;
`;

const StatSubtext = styled.div`
  font-size: 12px;
  color: ${(props) => props.color};
  font-weight: 500;
`;

const ReportStatCards = ({ reports = [] }) => {
  // 전체 데이터 목록을 기준으로 각 상태별 건수 계산
  const stats = useMemo(() => {
    const pendingCount = reports.filter((r) => r.status === 'PENDING').length;
    const reviewedCount = reports.filter((r) => r.status === 'REVIEWED').length;
    const dismissedCount = reports.filter(
      (r) => r.status === 'DISMISSED',
    ).length;
    const totalCount = reports.length;

    return {
      pendingCount,
      reviewedCount,
      dismissedCount,
      totalCount,
    };
  }, [reports]);

  return (
    <StatCardsContainer>
      <StatCard
        color="#FFF0F2"
        $borderColor="#FFD0D6"
      >
        <StatTitle>접수 대기</StatTitle>
        <StatValue>{stats.pendingCount}</StatValue>
        <StatSubtext color="#E5484D">미처리 건수</StatSubtext>
      </StatCard>

      <StatCard
        color="#FEFCE8"
        $borderColor="#FEF08A"
      >
        <StatTitle>검토 완료</StatTitle>
        <StatValue>{stats.reviewedCount}</StatValue>
        <StatSubtext color="#854D0E">조치 적용됨</StatSubtext>
      </StatCard>

      <StatCard
        color="#F0FDF4"
        $borderColor="#BBF7D0"
      >
        <StatTitle>기각 처리</StatTitle>
        <StatValue>{stats.dismissedCount}</StatValue>
        <StatSubtext color="#166534">무효/허위 신고</StatSubtext>
      </StatCard>

      <StatCard
        color="#EFF6FF"
        $borderColor="#BFDBFE"
      >
        <StatTitle>전체 신고</StatTitle>
        <StatValue>{stats.totalCount}</StatValue>
        <StatSubtext color="#1E40AF">누적 접수</StatSubtext>
      </StatCard>
    </StatCardsContainer>
  );
};

export default ReportStatCards;
