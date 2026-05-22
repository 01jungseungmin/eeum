import React, { useState } from 'react';
import styled from 'styled-components';
import {
  Users,
  Store,
  ShoppingBag,
  AlertTriangle,
  Download,
} from 'lucide-react';
import DashboardCard from '../../../components/dashboard/DashboardCard';
import AdminPendingActions from '../../../components/dashboard/admin/AdminPendingActions';
import AdminLiveActivities from '../../../components/dashboard/admin/AdminLiveActivities';
import AdminUserChart from '../../../components/dashboard/admin/AdminUserChart';
import AdminRegionChart from '../../../components/dashboard/admin/AdminRegionChart';

const DashboardWrapper = styled.div`
  padding: 30px;
  background-color: #fcfcfc;
  display: flex;
  flex-direction: column;
  gap: 24px;
  font-family: 'Pretendard', sans-serif;
`;

const AdminHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;

  .title-side {
    h1 {
      margin: 0 0 4px 0;
      font-size: 24px;
      font-weight: 700;
      color: #262626;
    }
    p {
      margin: 0;
      font-size: 13px;
      color: #8c8c8c;
    }
  }
  .action-side {
    display: flex;
    align-items: center;
    gap: 16px;
  }
`;

const ReportButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  background-color: #2d5a43;
  color: white;
  border: none;
  padding: 10px 16px;
  border-radius: 8px;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  &:hover {
    background-color: #1f3f2f;
  }
`;

const GridSection = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
`;

const BottomGridRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
`;

function DashboardPage() {
  const [searchTerm, setSearchTerm] = useState('');

  return (
    <DashboardWrapper>
      <AdminHeader>
        <div className="title-side">
          <h1>이웃 운영 현황</h1>
          <p>플랫폼 실시간 상태 및 인프라 대시보드</p>
        </div>
        <div className="action-side">
          <ReportButton onClick={() => alert('월간 플랫폼 데이터 추출')}>
            <Download size={16} /> 리포트 추출
          </ReportButton>
        </div>
      </AdminHeader>

      {/* 상단 통계 그리드: 검증된 DashboardCard 컴포넌트에 관리자 데이터 맵핑 */}
      <GridSection>
        <DashboardCard
          title="전체 회원"
          value="12,847"
          unit="명"
          icon={<Users size={20} />}
          iconBg="#edf5f1"
          iconColor="#2d5a43"
          trendText="↑ 이번 주 +182명"
          trendType="up"
        />
        <DashboardCard
          title="활성 사업장"
          value="438"
          unit="개"
          icon={<Store size={20} />}
          iconBg="#fffbe6"
          iconColor="#faad14"
          trendText="↑ 신규 입점 +12"
          trendType="up"
        />
        <DashboardCard
          title="오늘 거래"
          value="1,284"
          unit="건"
          icon={<ShoppingBag size={20} />}
          iconBg="#e6f7ff"
          iconColor="#1890ff"
          trendText="↑ 전일 대비 +8.2%"
          trendType="up"
        />
        <DashboardCard
          title="처리 대기"
          value="23"
          icon={<AlertTriangle size={20} />}
          iconBg="#fff5f5"
          iconColor="#ff4d4f"
          trendText="긴급 12 · 신고 5"
          trendType="down"
        />
      </GridSection>

      {/* 중간 그리드: 처리 대기 항목 / 실시간 활동 */}
      <BottomGridRow>
        <AdminPendingActions />
        <AdminLiveActivities />
      </BottomGridRow>

      {/* 하단 그리드: 주간 가입자 추이 / 지역별 활동 사용자 */}
      <BottomGridRow>
        <AdminUserChart />
        <AdminRegionChart />
      </BottomGridRow>
    </DashboardWrapper>
  );
}

export default DashboardPage;
