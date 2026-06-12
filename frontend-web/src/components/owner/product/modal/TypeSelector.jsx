import React from 'react';
import styled from 'styled-components';
// 🌟 Lucide 아이콘 임포트
import { ShoppingCart, Calendar, BookOpen } from 'lucide-react';

const TypeCardContainer = styled.div`
  display: flex;
  gap: 12px;
  margin-bottom: 25px;
`;

const TypeCard = styled.div`
  flex: 1;
  border: 1px solid ${(props) => (props.$active ? '#00a651' : '#eef0f2')};
  background: ${(props) => (props.$active ? '#f4fbf7' : '#fff')};
  border-radius: 14px;
  padding: 20px 10px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s ease;

  .icon-wrapper {
    width: 36px;
    height: 36px;
    border-radius: 8px;
    background: ${(props) => props.$iconBg};
    color: ${(props) => props.$iconColor};
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 12px;
  }

  .title {
    font-size: 13px;
    font-weight: bold;
    color: #333;
    margin-bottom: 6px;
  }

  .desc {
    font-size: 11px;
    color: #8e94a0;
    line-height: 1.4;
  }
`;

function TypeSelector({ currentType, onChangeType }) {
  return (
    <TypeCardContainer>
      <TypeCard
        $active={currentType === 'SALE'}
        $iconBg="#e6f7ed"
        $iconColor="#00a651"
        onClick={() => onChangeType('SALE')}
      >
        <div className="icon-wrapper">
          <ShoppingCart size={18} strokeWidth={2.5} />
        </div>
        <div className="title">판매 상품</div>
        <div className="desc">
          결제를 통해 구매 가능,
          <br />
          픽업 시간 설정 가능
        </div>
      </TypeCard>

      <TypeCard
        $active={currentType === 'PREORDER'}
        $iconBg="#fff9db"
        $iconColor="#f7a110"
        onClick={() => onChangeType('PREORDER')}
      >
        <div className="icon-wrapper">
          <Calendar size={18} strokeWidth={2.5} />
        </div>
        <div className="title">예약 상품</div>
        <div className="desc">
          결제 없이 예약만 가능,
          <br />
          방문 시간 설정 가능
        </div>
      </TypeCard>

      <TypeCard
        $active={currentType === 'MENU'}
        $iconBg="#f1f3f5"
        $iconColor="#666"
        onClick={() => onChangeType('MENU')}
      >
        <div className="icon-wrapper">
          <BookOpen size={18} strokeWidth={2.5} />
        </div>
        <div className="title">메뉴 상품</div>
        <div className="desc">
          메뉴 조회만 가능
          <br />
          &nbsp;
        </div>
      </TypeCard>
    </TypeCardContainer>
  );
}

export default TypeSelector;
