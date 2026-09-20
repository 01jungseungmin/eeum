import { useEffect, useState } from 'react';
import styled from 'styled-components';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  ResponsiveContainer,
  Tooltip,
} from 'recharts';
import { dashboardApi } from '../../../api/admin/dashboardApi';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  min-width: 0;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: #262626;
  }
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

const REGION_LIMIT = 6;

// 구·군 이름이 없는 시(세종 등)는 시·도 이름을 쓴다
const getRegionName = (region) => region.gunGu || region.siDo;

// 서울 중구 / 부산 중구처럼 이름이 겹치면 시·도 앞 두 글자를 붙여 구분한다
const buildChartData = (regions) => {
  const nameCount = {};
  regions.forEach((region) => {
    const name = getRegionName(region);
    nameCount[name] = (nameCount[name] || 0) + 1;
  });

  return regions.map((region) => {
    const name = getRegionName(region);
    const isDuplicated = nameCount[name] > 1;
    return {
      name: isDuplicated ? `${region.siDo.slice(0, 2)} ${name}` : name,
      fullName: [region.siDo, region.gunGu].filter(Boolean).join(' '),
      value: region.memberCount,
    };
  });
};

function AdminRegionChart() {
  const [chartData, setChartData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let isMounted = true;

    const fetchRegions = async () => {
      try {
        const response = await dashboardApi.getRegionMembers({
          limit: REGION_LIMIT,
        });
        if (isMounted && response.data?.success) {
          setChartData(buildChartData(response.data.data || []));
        }
      } catch (err) {
        console.error('지역별 활동 사용자 조회 실패:', err);
        if (isMounted) setError(true);
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    fetchRegions();

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <Card>
      <Header>
        <h3>지역별 활동 사용자</h3>
      </Header>

      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : error ? (
        <EmptyText>지역별 사용자를 불러오지 못했어요.</EmptyText>
      ) : chartData.length === 0 ? (
        <EmptyText>동네 인증을 마친 회원이 아직 없어요.</EmptyText>
      ) : (
        <ChartContainer>
          <ResponsiveContainer
            width="100%"
            height={200}
            minWidth={0}
          >
            <BarChart
              layout="vertical"
              data={chartData}
              margin={{ top: 0, right: 10, left: 10, bottom: 0 }}
            >
              <XAxis
                type="number"
                hide
              />
              <YAxis
                dataKey="name"
                type="category"
                axisLine={false}
                tickLine={false}
                stroke="#262626"
                fontSize={12}
                fontWeight={600}
                width={70}
              />
              <Tooltip
                labelFormatter={(_, payload) => payload?.[0]?.payload?.fullName}
                formatter={(value) => [`${value.toLocaleString()}명`, '회원']}
              />
              <Bar
                dataKey="value"
                fill="#2d5a43"
                radius={[0, 4, 4, 0]}
                barSize={12}
              />
            </BarChart>
          </ResponsiveContainer>
        </ChartContainer>
      )}
    </Card>
  );
}

export default AdminRegionChart;
