import React from 'react';
import styled from 'styled-components';
import {
  Building2,
  FileText,
  User,
  MapPin,
  Store,
  Package,
  Calendar,
} from 'lucide-react';

const Container = styled.div`
  background: white;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  margin-bottom: 30px;
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f5f5f5;
`;

const Title = styled.h3`
  font-size: 18px;
  font-weight: 700;
  color: #262626;
  margin: 0;
`;

const InfoGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr); // 2열 구조
  gap: 20px 40px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr; // 모바일에서는 1열
  }
`;

const InfoItem = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
`;

const IconWrapper = styled.div`
  width: 40px;
  height: 40px;
  background-color: #f6ffed; // 연한 녹색 배경
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #52c41a; // 아이콘 색상
`;

const TextContent = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const Label = styled.span`
  font-size: 13px;
  color: #8c8c8c;
`;

const Value = styled.span`
  font-size: 16px;
  font-weight: 600;
  color: #262626;
`;

const BusinessInfoBox = () => {
  const infoData = [
    {
      id: 1,
      label: '사업자 번호',
      value: '123-45-67890',
      icon: <FileText size={18} />,
    },
    {
      id: 2,
      label: '상호명',
      value: '맛있는 반찬가게',
      icon: <Store size={18} />,
    },
    { id: 3, label: '대표자', value: '김*영', icon: <User size={18} /> },
    {
      id: 4,
      label: '업종',
      value: '음식업 / 반찬가게',
      icon: <Package size={18} />,
    },
    {
      id: 5,
      label: '사업장 주소',
      value: '서울 마포구 서교동 123-4',
      icon: <MapPin size={18} />,
    },
    {
      id: 6,
      label: '서류 제출일',
      value: '2024-04-25',
      icon: <Calendar size={18} />,
    },
  ];

  return (
    <Container>
      <Header>
        <Building2 size={22} color="#2e7d32" />
        <Title>사업자 등록 정보</Title>
      </Header>

      <InfoGrid>
        {infoData.map((item) => (
          <InfoItem key={item.id}>
            <IconWrapper>{item.icon}</IconWrapper>
            <TextContent>
              <Label>{item.label}</Label>
              <Value>{item.value}</Value>
            </TextContent>
          </InfoItem>
        ))}
      </InfoGrid>
    </Container>
  );
};

export default BusinessInfoBox;
