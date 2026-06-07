// 📄 src/pages/owner/product/components/ProductHeader.jsx
import styled from 'styled-components';

const HeaderWrapper = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const TitleSection = styled.div`
  text-align: left;
  h2 {
    font-size: 22px;
    font-weight: bold;
    color: #333;
    margin: 0 0 5px 0;
  }
  p {
    font-size: 13px;
    color: #888;
    margin: 0;
  }
`;

const UtilitySection = styled.div`
  display: flex;
  align-items: center;
  gap: 20px;
`;

const TopSearchInput = styled.input`
  padding: 8px 15px;
  width: 240px;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  font-size: 13px;
  background-color: #fff;
`;

const ProfileBox = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  padding: 6px 12px;
  border-radius: 20px;
  border: 1px solid #e0e0e0;
  font-size: 13px;
  font-weight: 500;

  .avatar {
    width: 28px;
    height: 28px;
    background: #00a651;
    color: white;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
  }
`;

function ProductHeader() {
  return (
    <HeaderWrapper>
      <TitleSection>
        <h2>상품 관리</h2>
        <p>2026년 6월 5일 금요일 · 판매/예약/메뉴 상품을 등록하고 관리하세요</p>
      </TitleSection>
      <UtilitySection>
        <TopSearchInput placeholder="검색..." />
        <ProfileBox>
          <div className="avatar">김</div>
          <span>김사장 사장 회원 ▾</span>
        </ProfileBox>
      </UtilitySection>
    </HeaderWrapper>
  );
}

export default ProductHeader;
