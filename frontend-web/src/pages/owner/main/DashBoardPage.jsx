import styled from 'styled-components';
import { useOutletContext } from 'react-router-dom';
import {
  TrendingUp,
  ShoppingBag,
  Star,
  MessageSquare,
  CheckCircle,
  Clock,
  PlusCircle,
  CalendarClock,
} from 'lucide-react';
import DashboardCard from '../../../components/owner/dashboard/DashBoardCard';
import SalesChart from '../../../components/owner/dashboard/SalesChart';
import CategoryChart from '../../../components/owner/dashboard/CategoryChart';
import TopProducts from '../../../components/owner/dashboard/TopProducts';
import RecentOrders from '../../../components/owner/dashboard/recentOrders';
import RecentReviews from '../../../components/owner/dashboard/RecentReviews';

const DashboardWrapper = styled.div`
  padding: 30px;
  background-color: #fcfcfc;
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

const GridSection = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
`;

const WelcomeBanner = styled.div`
  background-color: #2d5a43;
  color: white;
  padding: 30px;
  border-radius: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .text-side {
    h3 {
      margin: 0 0 8px 0;
      font-size: 16px;
      font-weight: 500;
      opacity: 0.9;
    }
    h1 {
      margin: 0 0 12px 0;
      font-size: 26px;
      font-weight: 700;
    }
    p {
      margin: 0;
      font-size: 14px;
      opacity: 0.8;
    }
  }

  .button-side {
    display: flex;
    gap: 12px;
    button {
      padding: 10px 20px;
      border-radius: 8px;
      border: none;
      font-weight: 600;
      font-size: 14px;
      cursor: pointer;
    }
    .btn-check {
      background: rgba(255, 255, 255, 0.15);
      color: white;
    }
    .btn-add {
      background: white;
      color: #2d5a43;
    }
  }
`;

const BottomGridRow1 = styled.div`
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
`;

const BottomGridRow2 = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr); // 하단 3개 컴포넌트 균등 분할
  gap: 20px;
`;

// 원 단위 매출을 "만원" 단위로 반올림 표시
const toManwon = (value) => Math.round((value || 0) / 10000).toLocaleString();

function DashboardPage() {
  const { dashboardData } = useOutletContext();
  const d = dashboardData || {};

  return (
    <DashboardWrapper>
      <WelcomeBanner>
        <div className="text-side">
          <h3>
            좋은 아침이에요{d.storeName ? `, ${d.storeName} 사장님` : ''}! 👋
          </h3>
          <h1>오늘도 활기찬 하루 보내세요</h1>
          <p>
            오늘 {d.todayOrderCount ?? 0}건의 새 주문과{' '}
            {d.unansweredReviewCount ?? 0}개의 미답변 리뷰가 있어요
          </p>
        </div>
        <div className="button-side">
          <button className="btn-check">주문 확인</button>
          <button className="btn-add">상품 등록</button>
        </div>
      </WelcomeBanner>

      <GridSection>
        <DashboardCard
          title="이번 달 매출"
          value={toManwon(d.monthRevenue)}
          unit="만원"
          icon={<TrendingUp size={20} />}
          iconBg="#e6f7ff"
          iconColor="#1890ff"
        />
        <DashboardCard
          title="오늘 주문"
          value={d.todayOrderCount ?? 0}
          icon={<ShoppingBag size={20} />}
          iconBg="#f0f5ff"
          iconColor="#2f54eb"
        />
        <DashboardCard
          title="상점 평점"
          value={(d.averageRating ?? 0).toFixed(1)}
          unit="/ 5.0"
          icon={<Star size={20} />}
          iconBg="#fffbe6"
          iconColor="#faad14"
          subText={`리뷰 ${d.totalReviewCount ?? 0}개`}
        />
        <DashboardCard
          title="미답변 리뷰"
          value={d.unansweredReviewCount ?? 0}
          icon={<MessageSquare size={20} />}
          iconBg="#f9f0ff"
          iconColor="#722ed1"
          trendText={d.unansweredReviewCount > 0 ? '빠른 응답 필요' : undefined}
          trendType="warning"
        />
      </GridSection>

      <GridSection>
        <DashboardCard
          title="등록 상품"
          value={d.totalProductCount ?? 0}
          icon={<PlusCircle size={20} />}
          subText={`품절 ${d.soldOutProductCount ?? 0}개 포함`}
        />
        <DashboardCard
          title="대기 주문"
          value={d.pendingOrderCount ?? 0}
          icon={<Clock size={20} />}
          trendText={d.pendingOrderCount > 0 ? '확인 필요' : undefined}
          trendType="warning"
        />
        <DashboardCard
          title="대기 예약"
          value={d.pendingReservationCount ?? 0}
          icon={<CalendarClock size={20} />}
          trendText={d.pendingReservationCount > 0 ? '확인 필요' : undefined}
          trendType="warning"
        />
        <DashboardCard
          title="이번 달 주문"
          value={d.monthOrderCount ?? 0}
          icon={<CheckCircle size={20} />}
        />
      </GridSection>

      <BottomGridRow1>
        <SalesChart />
        <CategoryChart />
      </BottomGridRow1>

      <BottomGridRow2>
        <TopProducts />
        <RecentOrders />
        <RecentReviews />
      </BottomGridRow2>
    </DashboardWrapper>
  );
}

export default DashboardPage;
