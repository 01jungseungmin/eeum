import { useEffect, useState } from 'react';
import styled from 'styled-components';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  ResponsiveContainer,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import { dashboardApi } from '../../../api/admin/dashboardApi';
import {
  SIGNUP_TYPE_TABS,
  DAY_OF_WEEK_LABEL,
} from '../../../constants/dashboardConstants';

const ChartCard = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  min-width: 0;
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .title-side {
    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 700;
      color: #262626;
    }
    p {
      margin: 4px 0 0 0;
      font-size: 12px;
      color: #8c8c8c;
    }
  }
`;

const TabContainer = styled.div`
  display: flex;
  gap: 4px;
  background: #f5f5f5;
  padding: 4px;
  border-radius: 8px;
`;

const TabButton = styled.button`
  border: none;
  padding: 4px 12px;
  font-size: 12px;
  border-radius: 6px;
  cursor: pointer;
  background: ${(props) => (props.$active ? '#2d5a43' : 'transparent')};
  color: ${(props) => (props.$active ? 'white' : '#595959')};
  font-weight: ${(props) => (props.$active ? '700' : '500')};
`;

const ChartContainer = styled.div`
  width: 100%;
  height: 200px;
  position: relative;
  min-width: 0; /* CSS Grid 크기 계산 오류 방지 */
`;

const EmptyText = styled.div`
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #bfbfbf;
  font-size: 13px;
`;

const TREND_DAYS = 7;

// "2026-09-20" → "9/20"
const formatShortDate = (dateStr) => {
  const [, month, day] = dateStr.split('-');
  return `${Number(month)}/${Number(day)}`;
};

function AdminUserChart() {
  const [activeType, setActiveType] = useState('GENERAL');
  const [trend, setTrend] = useState({ total: 0, daily: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    // 탭을 빠르게 바꿨을 때 늦게 도착한 이전 응답이 화면을 덮어쓰지 않도록 취소 플래그를 둔다
    let isCancelled = false;

    const fetchTrend = async () => {
      setLoading(true);
      setError(false);
      try {
        const response = await dashboardApi.getSignupTrend({
          type: activeType,
          days: TREND_DAYS,
        });
        if (!isCancelled && response.data?.success) {
          const { total, daily } = response.data.data;
          setTrend({
            total,
            daily: (daily || []).map((item) => ({
              name: DAY_OF_WEEK_LABEL[item.dayOfWeek] || item.dayOfWeek,
              dateLabel: `${formatShortDate(item.date)} (${DAY_OF_WEEK_LABEL[item.dayOfWeek] || ''})`,
              value: item.count,
            })),
          });
        }
      } catch (err) {
        console.error('가입자 추이 조회 실패:', err);
        if (!isCancelled) setError(true);
      } finally {
        if (!isCancelled) setLoading(false);
      }
    };

    fetchTrend();

    return () => {
      isCancelled = true;
    };
  }, [activeType]);

  return (
    <ChartCard>
      <CardHeader>
        <div className="title-side">
          <h3>주간 가입자 추이</h3>
          <p>최근 7일 신규 가입 트렌드</p>
        </div>
        <TabContainer>
          {SIGNUP_TYPE_TABS.map((tab) => (
            <TabButton
              key={tab.value}
              $active={activeType === tab.value}
              onClick={() => setActiveType(tab.value)}
            >
              {tab.label}
            </TabButton>
          ))}
        </TabContainer>
      </CardHeader>

      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : error ? (
        <EmptyText>가입자 추이를 불러오지 못했어요.</EmptyText>
      ) : (
        <ChartContainer>
          {/* 2. minWidth={0} 추가 및 height를 숫자로 직접 지정 */}
          <ResponsiveContainer
            width="100%"
            height={200}
            minWidth={0}
          >
            <BarChart
              data={trend.daily}
              margin={{ top: 10, right: 10, left: -25, bottom: 0 }}
            >
              <CartesianGrid
                strokeDasharray="3 3"
                vertical={false}
                stroke="#f5f5f5"
              />
              <XAxis
                dataKey="name"
                stroke="#bfbfbf"
                fontSize={11}
                tickLine={false}
              />
              <YAxis
                stroke="#bfbfbf"
                fontSize={11}
                axisLine={false}
                tickLine={false}
                allowDecimals={false}
              />
              <Tooltip
                labelFormatter={(_, payload) => payload?.[0]?.payload?.dateLabel}
                formatter={(value) => [`${value}명`, '가입자']}
              />
              <Bar
                dataKey="value"
                fill="#2d5a43"
                radius={[4, 4, 0, 0]}
                barSize={26}
              />
            </BarChart>
          </ResponsiveContainer>
        </ChartContainer>
      )}

      {!loading && !error && (
        <div
          style={{
            textAlign: 'center',
            fontSize: '12px',
            color: '#bfbfbf',
            marginTop: '12px',
          }}
        >
          최근 {TREND_DAYS}일 합계 {trend.total.toLocaleString()}명
        </div>
      )}
    </ChartCard>
  );
}

export default AdminUserChart;
