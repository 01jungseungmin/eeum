import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { PieChart, Pie, Cell, Tooltip } from 'recharts';
import { storeApi } from '../../../api/owner/storeApi';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  height: 100%;
`;

const Title = styled.h3`
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #262626;
`;
const SubTitle = styled.p`
  margin: 4px 0 15px 0;
  font-size: 12px;
  color: #8c8c8c;
`;

const LegendContainer = styled.div`
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const LegendItem = styled.div`
  display: flex;
  justify-content: space-between;
  font-size: 13px;

  .label-side {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #595959;
  }
  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background-color: ${(props) => props.$color};
  }
  .value-side {
    font-weight: 700;
    color: #262626;
  }
`;

const EmptyText = styled.div`
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

// 상위 카테고리가 색상 4개(마지막은 "기타")에 들어가도록 나머지는 기타로 묶는다
const CHART_COLORS = ['#2d5a43', '#7cb342', '#c5e1a5', '#bfbfbf'];
const MAX_SLICES = CHART_COLORS.length;

const pad = (n) => String(n).padStart(2, '0');

const toLocalDateTime = (date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;

// "이번 달 기준" = 이번 달 1일 00:00 ~ 지금
const getThisMonthRange = () => {
  const now = new Date();
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0);
  return { from: toLocalDateTime(monthStart), to: toLocalDateTime(now) };
};

// API 응답(수량 내림차순) → 차트 데이터. 카테고리가 많으면 상위 N-1개 + 기타로 합친다.
const buildChartData = (categories) => {
  const rows = categories.map((category) => ({
    name: category.categoryName,
    value: Number(category.quantityRatio),
    quantity: category.soldQuantity,
  }));

  if (rows.length > MAX_SLICES) {
    const top = rows.slice(0, MAX_SLICES - 1);
    const rest = rows.slice(MAX_SLICES - 1);
    rows.length = 0;
    rows.push(...top, {
      name: '기타',
      value: Math.round(rest.reduce((sum, row) => sum + row.value, 0) * 10) / 10,
      quantity: rest.reduce((sum, row) => sum + row.quantity, 0),
    });
  }

  return rows.map((row, index) => ({ ...row, color: CHART_COLORS[index] }));
};

function CategoryChart() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let isMounted = true;

    const fetchCategorySales = async () => {
      try {
        const { from, to } = getThisMonthRange();
        const response = await storeApi.getCategorySales(from, to);

        if (isMounted && response?.success) {
          setData(buildChartData(response.data || []));
        }
      } catch (err) {
        console.error('카테고리별 판매 조회 실패:', err);
        if (isMounted) setError(true);
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    fetchCategorySales();

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <Card>
      <Title>카테고리별 판매</Title>
      <SubTitle>이번 달 기준 · 판매 수량 비율</SubTitle>

      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : error ? (
        <EmptyText>카테고리별 판매를 불러오지 못했어요.</EmptyText>
      ) : data.length === 0 ? (
        <EmptyText>이번 달 판매된 상품이 없습니다.</EmptyText>
      ) : (
        <>
          <div
            style={{
              width: '100%',
              height: 140,
              display: 'flex',
              justifyContent: 'center',
            }}
          >
            <PieChart
              width={200}
              height={140}
            >
              <Pie
                data={data}
                cx="50%"
                cy="50%"
                innerRadius={45}
                outerRadius={60}
                paddingAngle={3}
                dataKey="value"
              >
                {data.map((entry) => (
                  <Cell
                    key={entry.name}
                    fill={entry.color}
                  />
                ))}
              </Pie>
              <Tooltip
                formatter={(value, name, item) => [
                  `${value}% (${item.payload.quantity}개)`,
                  name,
                ]}
              />
            </PieChart>
          </div>

          <LegendContainer>
            {data.map((item) => (
              <LegendItem
                key={item.name}
                $color={item.color}
              >
                <div className="label-side">
                  <span className="dot" />
                  <span>{item.name}</span>
                </div>
                <div className="value-side">{item.value}%</div>
              </LegendItem>
            ))}
          </LegendContainer>
        </>
      )}
    </Card>
  );
}

export default CategoryChart;
