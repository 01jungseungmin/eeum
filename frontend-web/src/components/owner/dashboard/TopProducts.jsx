import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { storeApi } from '../../../api/owner/storeApi';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
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
  }
  .more {
    display: flex;
    align-items: center;
    font-size: 12px;
    color: #52c41a;
    cursor: pointer;
    font-weight: 600;
  }
`;

const RankList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const RankRow = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;

const RankBadge = styled.div`
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #ffffff;
  background: ${(props) =>
    props.$rank === 1 ? '#f5a623' : props.$rank === 2 ? '#9ca3af' : '#c98b5e'};
`;

const RankBody = styled.div`
  flex: 1;
  min-width: 0;
`;

const RankTop = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
  gap: 8px;

  .name {
    font-size: 13px;
    font-weight: 600;
    color: #262626;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .qty {
    flex-shrink: 0;
    font-size: 12px;
    font-weight: 700;
    color: #5fa07e;
  }
`;

const GaugeTrack = styled.div`
  width: 100%;
  height: 8px;
  border-radius: 4px;
  background: #f1f3f5;
  overflow: hidden;
`;

const GaugeFill = styled.div`
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #7fc8a0, #5fa07e);
  width: ${(props) => props.$percent}%;
  transition: width 0.4s ease;
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 30px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

// "최근 한 달" = 정확히 30일 전 ~ 지금 (달력상 월 경계 대신 고정 30일 창을 사용해
// 말일 근처에서 setMonth()가 다음 달로 밀리는 문제를 피한다), LocalDateTime 형식으로 변환
const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;

const getLastMonthRange = () => {
  const pad = (n) => String(n).padStart(2, '0');
  const toLocalDateTime = (date) =>
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
      date.getHours(),
    )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;

  const to = new Date();
  const from = new Date(to.getTime() - THIRTY_DAYS_MS);

  return { from: toLocalDateTime(from), to: toLocalDateTime(to) };
};

function TopProducts() {
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchTopProducts = async () => {
    try {
      setLoading(true);
      const { from, to } = getLastMonthRange();
      const response = await storeApi.getProductSales(from, to);

      if (response?.success) {
        console.log('인기 상품 조회 성공:', response.data);
        setProducts((response.data || []).slice(0, 3));
      }
    } catch (error) {
      console.error('인기 상품 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    queueMicrotask(() => fetchTopProducts());
  }, []);

  const maxQuantity = products[0]?.soldQuantity || 1;

  return (
    <Card>
      <Header>
        <h3>인기 상품 TOP 3</h3>
        <span
          className="more"
          onClick={() => navigate('/products')}
        >
          전체 보기 <ChevronRight size={14} />
        </span>
      </Header>

      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : products.length === 0 ? (
        <EmptyText>최근 한 달간 판매된 상품이 없습니다.</EmptyText>
      ) : (
        <RankList>
          {products.map((product, index) => (
            <RankRow key={product.productId}>
              <RankBadge $rank={index + 1}>{index + 1}</RankBadge>
              <RankBody>
                <RankTop>
                  <span className="name">{product.productName}</span>
                  <span className="qty">{product.soldQuantity}개 판매</span>
                </RankTop>
                <GaugeTrack>
                  <GaugeFill
                    $percent={(product.soldQuantity / maxQuantity) * 100}
                  />
                </GaugeTrack>
              </RankBody>
            </RankRow>
          ))}
        </RankList>
      )}
    </Card>
  );
}

export default TopProducts;
