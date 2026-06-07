// 📄 src/pages/owner/product/components/ProductStats.jsx
import styled from 'styled-components';

const StatsSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 15px;
`;

const CardGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 15px;
`;

const StatCard = styled.div`
  background: white;
  padding: 20px;
  border-radius: 12px;
  border: 1px solid #eef0f2;
  text-align: left;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);

  .label {
    font-size: 12px;
    color: #888;
    margin-bottom: 8px;
  }
  .count {
    font-size: 26px;
    font-weight: bold;
    color: ${(props) => props.$color || '#333'};
  }
`;

const BannerGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 15px;
`;

const InfoBanner = styled.div`
  background: ${(props) => props.$bgColor};
  border: 1px solid ${(props) => props.$borderColor};
  padding: 15px 20px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;

  .icon {
    font-size: 20px;
  }
  .text-group {
    display: flex;
    flex-direction: column;
    gap: 2px;
    h4 {
      margin: 0;
      font-size: 13px;
      font-weight: bold;
      color: ${(props) => props.$titleColor};
    }
    p {
      margin: 0;
      font-size: 11px;
      color: #666;
    }
  }
`;

function ProductStats() {
  return (
    <StatsSection>
      {/* 수치 요약 카드 라인 */}
      <CardGrid>
        <StatCard>
          <div className="label">전체 상품</div>
          <div className="count" style={{ color: '#00a651' }}>
            6
          </div>
        </StatCard>
        <StatCard>
          <div className="label">판매 상품</div>
          <div className="count" style={{ color: '#4361ee' }}>
            4
          </div>
        </StatCard>
        <StatCard>
          <div className="label">예약 상품</div>
          <div className="count" style={{ color: '#f7a110' }}>
            1
          </div>
        </StatCard>
        <StatCard>
          <div className="label">메뉴 상품</div>
          <div className="count" style={{ color: '#555' }}>
            1
          </div>
        </StatCard>
      </CardGrid>

      {/* 상품 안내 배너 라인 */}
      <BannerGrid>
        <InfoBanner
          $bgColor="#f4f7ff"
          $borderColor="#e1e8ff"
          $titleColor="#4361ee"
        >
          <span className="icon">🛒</span>
          <div className="text-group">
            <h4>판매 상품</h4>
            <p>결제를 통해 구매 가능, 픽업 시간 설정 가능</p>
          </div>
        </InfoBanner>
        <InfoBanner
          $bgColor="#fffcf4"
          $borderColor="#ffeec7"
          $titleColor="#f7a110"
        >
          <span className="icon">📅</span>
          <div className="text-group">
            <h4>예약 상품</h4>
            <p>결제 없이 예약만 가능, 방문 시간 설정 가능</p>
          </div>
        </InfoBanner>
        <InfoBanner
          $bgColor="#f8f9fa"
          $borderColor="#e9ecef"
          $titleColor="#555"
        >
          <span className="icon">📖</span>
          <div className="text-group">
            <h4>메뉴 상품</h4>
            <p>메뉴 조회만 가능</p>
          </div>
        </InfoBanner>
      </BannerGrid>
    </StatsSection>
  );
}

export default ProductStats;
