import { useState } from 'react';
import styled from 'styled-components';
import {
  TrendingUp,
  Clock,
  CreditCard,
  CheckCircle2,
  BarChart3,
  FileText,
  Wallet,
} from 'lucide-react';
import DashboardCard from '../../../components/owner/dashboard/DashBoardCard';
import SettlementPendingBanner from '../../../components/owner/settlement/SettlementPendingBanner';
import RevenueOverviewTab from '../../../components/owner/settlement/RevenueOverviewTab';
import WeeklySettlementTab from '../../../components/owner/settlement/WeeklySettlementTab';
import WithdrawalTab from '../../../components/owner/settlement/WithdrawalTab';
import { useSettlementData } from '../../../hooks/useSettlementData';

const Wrapper = styled.div`
  padding: 30px;
  background-color: #fcfcfc;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const CardGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;

  @media (max-width: 1100px) {
    grid-template-columns: repeat(2, 1fr);
  }
`;

const ErrorText = styled.div`
  text-align: center;
  padding: 20px;
  color: #cf1322;
  background: #fff1f0;
  border-radius: 12px;
  font-size: 13px;
`;

const TabBar = styled.div`
  display: flex;
  gap: 6px;
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 14px;
  padding: 6px;
  width: fit-content;
`;

const TabButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: ${(props) => (props.$active ? '#2d5a43' : 'transparent')};
  color: ${(props) => (props.$active ? 'white' : '#595959')};
  font-size: 14px;
  font-weight: 700;
  padding: 10px 18px;
  border-radius: 10px;
  cursor: pointer;
`;

const toManwon = (value) => Math.round(Number(value || 0) / 10000).toLocaleString();

const TABS = [
  { id: 'overview', label: '매출 현황', icon: BarChart3 },
  { id: 'weekly', label: '정산 내역', icon: FileText },
  { id: 'withdrawal', label: '출금 관리', icon: Wallet },
];

function SalesSettlementPage() {
  const { revenues, weeklySettlements, summary, loading, error } =
    useSettlementData();
  const [activeTab, setActiveTab] = useState('overview');

  return (
    <Wrapper>
      <CardGrid>
        <DashboardCard
          title="이번 주 매출"
          value={toManwon(summary.weekRevenue)}
          unit="만원"
          icon={<TrendingUp size={20} />}
          iconBg="#e6f7ff"
          iconColor="#1890ff"
        />
        <DashboardCard
          title="정산 대기"
          value={
            summary.pendingSettlement
              ? toManwon(summary.pendingSettlement.payoutAmount)
              : 0
          }
          unit="만원"
          icon={<Clock size={20} />}
          iconBg="#fff7e6"
          iconColor="#fa8c16"
          subText={
            summary.pendingSettlement ? '정산 처리 예정' : '대기 중인 정산 없음'
          }
        />
        <DashboardCard
          title="이번 달 수수료"
          value={Math.round(summary.monthFee).toLocaleString()}
          unit="원"
          icon={<CreditCard size={20} />}
          iconBg="#f0f5ff"
          iconColor="#2f54eb"
        />
        <DashboardCard
          title="누적 정산 완료"
          value={Math.round(summary.cumulativeCompleted).toLocaleString()}
          unit="원"
          icon={<CheckCircle2 size={20} />}
          iconBg="#edf5f1"
          iconColor="#2d5a43"
        />
      </CardGrid>

      {error && <ErrorText>{error}</ErrorText>}

      <SettlementPendingBanner
        settlement={summary.pendingSettlement}
        onClick={() => setActiveTab('weekly')}
      />

      <TabBar>
        {TABS.map(({ id, label, icon: Icon }) => (
          <TabButton
            key={id}
            $active={activeTab === id}
            onClick={() => setActiveTab(id)}
          >
            <Icon size={16} />
            {label}
          </TabButton>
        ))}
      </TabBar>

      {activeTab === 'overview' && (
        <RevenueOverviewTab
          revenues={revenues}
          loading={loading}
        />
      )}
      {activeTab === 'weekly' && (
        <WeeklySettlementTab
          weeklySettlements={weeklySettlements}
          loading={loading}
        />
      )}
      {activeTab === 'withdrawal' && <WithdrawalTab />}
    </Wrapper>
  );
}

export default SalesSettlementPage;
