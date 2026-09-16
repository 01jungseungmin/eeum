import { useState } from 'react';
import styled from 'styled-components';
import { Crown, MapPin, Siren, BarChart3, Megaphone } from 'lucide-react';
import PlanUpgradeModal from './modal/PlanUpgradeModal';

// FREE 플랜에서는 아래 4개 위젯이 전부 같은 사유(베이직 미만)로 막힌다.
// 각자 따로 "업그레이드 필요" 카드를 그리면 같은 문구/버튼이 4번 반복되고
// 화면 절반이 빈 공간으로 보여서, 하나의 배너로 묶어서 보여준다.
const LOCKED_FEATURES = [
  { icon: MapPin, label: '생활권 매칭' },
  { icon: Siren, label: '운영 위험 조기경보' },
  { icon: BarChart3, label: 'AI 활동 요약' },
  { icon: Megaphone, label: '마케팅 자동화' },
];

const BannerContainer = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 28px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 16px;
`;

const IconBadge = styled.div`
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background-color: #fef3c7;
  color: #d97706;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const Title = styled.h3`
  font-size: 17px;
  font-weight: 700;
  color: #111827;
  margin: 0;
`;

const Subtitle = styled.p`
  font-size: 13px;
  color: #6b7280;
  margin: 0;
`;

const FeatureRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
`;

const FeatureTag = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background-color: #f9fafb;
  border: 1px solid #f0f0f0;
  color: #4b5563;
  font-size: 12px;
  font-weight: 600;
  padding: 6px 12px;
  border-radius: 20px;
`;

const UpgradeButton = styled.button`
  background-color: #47a075;
  color: #ffffff;
  border: none;
  padding: 12px 28px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #3b8762;
  }
`;

export default function AiBasicUpgradeBanner() {
  const [isUpgradeModalOpen, setIsUpgradeModalOpen] = useState(false);

  return (
    <>
      <BannerContainer>
        <IconBadge>
          <Crown size={24} />
        </IconBadge>
        <div>
          <Title>베이직 플랜으로 4가지 AI 기능을 더 사용해보세요</Title>
          <Subtitle>지금 플랜에서는 아래 기능이 잠겨 있어요</Subtitle>
        </div>
        <FeatureRow>
          {LOCKED_FEATURES.map(({ icon: Icon, label }) => (
            <FeatureTag key={label}>
              <Icon size={13} />
              {label}
            </FeatureTag>
          ))}
        </FeatureRow>
        <UpgradeButton onClick={() => setIsUpgradeModalOpen(true)}>
          <Crown size={16} /> 플랜 업그레이드
        </UpgradeButton>
      </BannerContainer>
      <PlanUpgradeModal
        isOpen={isUpgradeModalOpen}
        onClose={() => setIsUpgradeModalOpen(false)}
        errorMessage="베이직 플랜부터 이용할 수 있는 기능이에요."
      />
    </>
  );
}
