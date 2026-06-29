import { Clock, Shield, CircleCheckBig, CircleX } from 'lucide-react';
import React from 'react';
import styled from 'styled-components';

const BannerContainer = styled.div`
  background-color: ${(props) => props.$bgColor};
  border-radius: 20px;
  padding: 30px;
  color: white;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
  margin-bottom: 30px;

  /* 우측 배경 원형 디자인 (이미지 참고) */
  &::after {
    content: '';
    position: absolute;
    right: -50px;
    top: -50px;
    width: 200px;
    height: 200px;
    background: rgba(255, 255, 255, 0.1);
    border-radius: 50%;
  }
`;

const TopSection = styled.div`
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 25px;
`;

const IconBox = styled.div`
  width: 50px;
  height: 50px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
`;

const StatusInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const Label = styled.span`
  font-size: 14px;
  opacity: 0.8;
`;

const BadgeGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const StatusIcon = styled.span`
  color: ${(props) => props.$color};
  font-size: 18px;
  font-weight: bold;
`;

const StatusBadge = styled.div`
  background: ${(props) => props.$bg};
  color: ${(props) => props.$color};
  padding: 2px 12px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: bold;
`;

const Description = styled.p`
  font-size: 18px;
  font-weight: 600;
  line-height: 1.5;
  margin: 0;
  margin-bottom: 25px;
  word-break: keep-all;
`;

const BottomSection = styled.div`
  display: flex;
  gap: 10px;
`;

const InfoRow = styled.div`
  display: flex;
  gap: 10px;
`;

const InfoChip = styled.div`
  background: rgba(255, 255, 255, 0.15);
  padding: 8px 16px;
  border-radius: 10px;
  font-size: 14px;
`;

const ReSubmitButton = styled.button`
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  padding: 10px 20px;
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: bold;
  transition: background 0.2s;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
  }
`;

const iconProps = { size: 20, strokeWidth: 1.5 };

const STATUS_CONFIG = {
  PENDING: {
    bgColor: '#2D3E33',
    title: '심사 중',
    titleColor: '#856404',
    titleBg: '#FFFBE6',
    icon: <Clock {...iconProps} />,
    desc: '제출하신 사업자 등록증을 검토 중입니다. 영업일 기준 1~3일 내로 결과를 안내해 드립니다.',
  },
  APPROVED: {
    bgColor: '#2E7D32',
    title: '승인 완료',
    titleColor: '#52C41A',
    titleBg: '#F6FFED',
    icon: <CircleCheckBig {...iconProps} />,
    desc: '이음 플랫폼 입점이 승인되었습니다! 이제 고객에게 상점이 노출됩니다.',
  },
  REJECTED: {
    bgColor: '#921B1B',
    title: '승인 반려',
    titleColor: '#FF4D4F',
    titleBg: '#FFF1F0',
    icon: <CircleX {...iconProps} />,
    desc: '입점 심사에서 보완이 필요한 사항이 발견되었습니다. 아래 내용을 확인하고 재제출해주세요.',
  },
};

const AuthStatusBanner = ({ status }) => {
  const config = STATUS_CONFIG[status];

  return (
    <BannerContainer $bgColor={config.bgColor}>
      <TopSection>
        <IconBox>
          <Shield />
        </IconBox>
        <StatusInfo>
          <Label>사업자 인증 상태</Label>
          <BadgeGroup>
            <StatusIcon $color={config.titleColor}>{config.icon}</StatusIcon>
            <StatusBadge $color={config.titleColor} $bg={config.titleBg}>
              {config.title}
            </StatusBadge>
          </BadgeGroup>
        </StatusInfo>
      </TopSection>

      <Description>{config.desc}</Description>

      {/* 하단 영역: 상태에 따라 다른 UI 렌더링 */}
      <BottomSection>
        {status === 'APPROVED' && (
          <InfoRow>
            <InfoChip>입점일: 2024-04-23</InfoChip>
            <InfoChip>상점 ID: SHOP-20240423-001</InfoChip>
          </InfoRow>
        )}

        {status === 'REJECTED' && (
          <ReSubmitButton>
            <span>🔄</span> 사업자 등록증 재제출
          </ReSubmitButton>
        )}
      </BottomSection>
    </BannerContainer>
  );
};

export default AuthStatusBanner;
