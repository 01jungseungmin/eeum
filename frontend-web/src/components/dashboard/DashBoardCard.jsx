import React from 'react';
import styled from 'styled-components';

const CardContainer = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 140px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
`;

const CardTop = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
`;

const TitleSection = styled.div`
  .title {
    font-size: 13px;
    color: #8c8c8c;
    margin-bottom: 8px;
    font-weight: 500;
  }
  .value-wrapper {
    display: flex;
    align-items: baseline;
    gap: 4px;
  }
  .value {
    font-size: 26px;
    font-weight: 700;
    color: #262626;
  }
  .unit {
    font-size: 16px;
    font-weight: 600;
    color: #262626;
  }
`;

const IconWrapper = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background-color: ${(props) => props.$bgColor || '#f5f5f5'};
  color: ${(props) => props.$iconColor || '#595959'};
  display: flex;
  align-items: center;
  justify-content: center;
`;

const CardBottom = styled.div`
  margin-top: 16px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
`;

const TrendBadge = styled.span`
  color: ${(props) =>
    props.$isUp ? '#52c41a' : props.$isWarning ? '#faad14' : '#ff4d4f'};
  font-weight: 600;
  display: flex;
  align-items: center;
`;

const SubText = styled.span`
  color: #8c8c8c;
`;

function DashboardCard({
  title,
  value,
  unit = '건',
  icon,
  iconBg,
  iconColor,
  trendText,
  subText,
  trendType, // 'up' | 'down' | 'warning'
}) {
  return (
    <CardContainer>
      <CardTop>
        <TitleSection>
          <div className="title">{title}</div>
          <div className="value-wrapper">
            <span className="value">{value}</span>
            {unit && <span className="unit">{unit}</span>}
          </div>
        </TitleSection>
        <IconWrapper $bgColor={iconBg} $iconColor={iconColor}>
          {icon}
        </IconWrapper>
      </CardTop>

      <CardBottom>
        {trendText && (
          <TrendBadge
            $isUp={trendType === 'up'}
            $isWarning={trendType === 'warning'}
          >
            {trendText}
          </TrendBadge>
        )}
        <SubText>{subText}</SubText>
      </CardBottom>
    </CardContainer>
  );
}

export default DashboardCard;
