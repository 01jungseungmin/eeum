import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { Wallet } from 'lucide-react';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 48px 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 12px;
`;

const IconCircle = styled.div`
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #edf5f1;
  color: #2d5a43;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
`;

const Title = styled.h4`
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #262626;
`;

const Desc = styled.p`
  margin: 0;
  font-size: 13px;
  color: #8c8c8c;
  line-height: 1.6;
  max-width: 360px;
`;

const LinkButton = styled.button`
  margin-top: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  border: 1px solid #2d5a43;
  background: white;
  color: #2d5a43;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;

  &:hover {
    background: #edf5f1;
  }
`;

// 정산 지급 계좌 등록/변경은 상점 관리 > 입점 정보의 정산 계좌 설정에서 이뤄지고 있어,
// 출금 관리 자체(자동 출금 신청/내역 조회)를 지원하는 백엔드 API는 아직 없다.
// 데이터를 지어내는 대신, 실제로 계좌를 관리할 수 있는 곳으로 안내한다.
function WithdrawalTab() {
  const navigate = useNavigate();

  return (
    <Card>
      <IconCircle>
        <Wallet size={24} />
      </IconCircle>
      <Title>출금 관리는 준비 중이에요</Title>
      <Desc>
        정산 지급 계좌 등록·변경은 상점 관리 페이지에서 가능해요. 출금 신청 및
        출금 내역 조회 기능은 곧 추가될 예정입니다.
      </Desc>
      <LinkButton onClick={() => navigate('/store')}>
        정산 계좌 관리하러 가기
      </LinkButton>
    </Card>
  );
}

export default WithdrawalTab;
