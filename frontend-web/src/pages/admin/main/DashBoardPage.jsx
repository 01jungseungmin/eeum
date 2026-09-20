import { useEffect, useState } from 'react';
import styled from 'styled-components';
import {
  Users,
  Store,
  ShoppingBag,
  AlertTriangle,
  Download,
} from 'lucide-react';
import DashboardCard from '../../../components/owner/dashboard/DashBoardCard';
import AdminPendingActions from '../../../components/admin/dashboard/AdminPendingActions';
import AdminLiveActivities from '../../../components/admin/dashboard/AdminLiveActivities';
import AdminUserChart from '../../../components/admin/dashboard/AdminUserChart';
import AdminRegionChart from '../../../components/admin/dashboard/AdminRegionChart';
import { dashboardApi } from '../../../api/admin/dashboardApi';

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

const ErrorBanner = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 10px;
  background: #fff1f0;
  border: 1px solid #ffccc7;
  color: #cf1322;
  font-size: 13px;

  button {
    border: none;
    background: #cf1322;
    color: white;
    padding: 6px 12px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
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

// 숫자를 천 단위 콤마로 표시. 아직 못 불러왔으면 '-'
const formatCount = (value) =>
  value === undefined || value === null ? '-' : Number(value).toLocaleString();

// 전일 대비 증감률(%) → 카드 하단 문구. 어제 거래가 0건이면 서버가 null을 준다.
const getOrderTrend = (summary) => {
  if (!summary) return {};

  const rate = summary.orderChangeRate;
  if (rate === null || rate === undefined) {
    return { trendText: '전일 거래 없음', trendType: 'warning' };
  }

  const value = Number(rate);
  if (value > 0) {
    return { trendText: `↑ 전일 대비 +${value}%`, trendType: 'up' };
  }
  if (value < 0) {
    return { trendText: `↓ 전일 대비 ${value}%`, trendType: 'down' };
  }
  return { trendText: '전일과 동일', trendType: 'warning' };
};

function DashboardPage() {
  const [summary, setSummary] = useState(null);
  const [summaryError, setSummaryError] = useState(false);

  const fetchSummary = async () => {
    try {
      setSummaryError(false);
      const response = await dashboardApi.getSummary();
      if (response.data?.success) {
        setSummary(response.data.data);
      }
    } catch (error) {
      console.error('대시보드 요약 조회 실패:', error);
      setSummaryError(true);
    }
  };

  useEffect(() => {
    queueMicrotask(() => fetchSummary());
  }, []);

  const orderTrend = getOrderTrend(summary);

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

      {summaryError && (
        <ErrorBanner>
          <span>상단 지표를 불러오지 못했어요.</span>
          <button onClick={fetchSummary}>다시 시도</button>
        </ErrorBanner>
      )}

      {/* 상단 통계 그리드: 검증된 DashboardCard 컴포넌트에 관리자 데이터 맵핑 */}
      <GridSection>
        <DashboardCard
          title="전체 회원"
          value={formatCount(summary?.totalMembers)}
          unit="명"
          icon={<Users size={20} />}
          iconBg="#edf5f1"
          iconColor="#2d5a43"
          trendText={
            summary
              ? `↑ 이번 주 +${formatCount(summary.newMembersThisWeek)}명`
              : ''
          }
          trendType="up"
        />
        <DashboardCard
          title="활성 사업장"
          value={formatCount(summary?.activeStores)}
          unit="개"
          icon={<Store size={20} />}
          iconBg="#fffbe6"
          iconColor="#faad14"
          trendText={
            summary
              ? `↑ 이번 주 신규 +${formatCount(summary.newStoresThisWeek)}`
              : ''
          }
          trendType="up"
        />
        <DashboardCard
          title="오늘 거래"
          value={formatCount(summary?.todayOrders)}
          unit="건"
          icon={<ShoppingBag size={20} />}
          iconBg="#e6f7ff"
          iconColor="#1890ff"
          {...orderTrend}
        />
        <DashboardCard
          title="처리 대기"
          value={formatCount(summary?.pendingTotal)}
          icon={<AlertTriangle size={20} />}
          iconBg="#fff5f5"
          iconColor="#ff4d4f"
          trendText={
            summary
              ? `사장 승인 ${summary.pendingOwnerApprovals} · 신고 ${summary.pendingReports} · 문의 ${summary.pendingInquiries}`
              : ''
          }
          trendType={summary?.pendingTotal > 0 ? 'down' : 'warning'}
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
