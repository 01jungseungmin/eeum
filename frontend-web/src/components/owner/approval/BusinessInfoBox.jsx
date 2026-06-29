import React, { useEffect, useState } from 'react';
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
import { accountApi } from '../../../api/owner/accountApi';

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
  grid-template-columns: repeat(2, 1fr);
  gap: 20px 40px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
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
  background-color: #f6ffed;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #52c41a;
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

const LoadingText = styled.div`
  padding: 24px;
  text-align: center;
  color: #8c8c8c;
  font-size: 14px;
`;

const maskName = (name) => {
  if (!name) return '-';
  if (name.length <= 2) return name.charAt(0) + '*';
  return (
    name.charAt(0) + '*'.repeat(name.length - 2) + name.charAt(name.length - 1)
  );
};

const formatBusinessNumber = (num) => {
  if (!num) return '-';
  const clean = num.replace(/[^0-9]/g, '');
  if (clean.length === 10) {
    return `${clean.slice(0, 3)}-${clean.slice(3, 5)}-${clean.slice(5)}`;
  }
  return num;
};

const formatDate = (dateTimeStr) => {
  if (!dateTimeStr) return '-';
  return dateTimeStr.split('T')[0];
};

const BusinessInfoBox = () => {
  const [ownerData, setOwnerData] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchOwnerBusinessInfo = async () => {
    try {
      setLoading(true);

      const response = await accountApi.getOwnerAccountInfo();

      if (response.data.success) {
        console.log(response.data.data);
        setOwnerData(response.data.data);
      }
    } catch (error) {
      console.error('사업자 정보 로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOwnerBusinessInfo();
  }, []);

  if (loading) {
    return (
      <Container>
        <LoadingText>사업자 정보를 안전하게 불러오는 중입니다...</LoadingText>
      </Container>
    );
  }

  const data = ownerData || {};

  const infoData = [
    {
      id: 1,
      label: '사업자 번호',
      value: formatBusinessNumber(data.businessNumber),
      icon: <FileText size={18} />,
    },
    {
      id: 2,
      label: '상호명',
      value: data.storeName || '-',
      icon: <Store size={18} />,
    },
    {
      id: 3,
      label: '대표자',
      value: maskName(data.ownerName),
      icon: <User size={18} />,
    },
    {
      id: 4,
      label: '업종',
      value: data.storeCategoryName ? `${data.storeCategoryName}업` : '-',
      icon: <Package size={18} />,
    },
    {
      id: 5,
      label: '사업장 주소',
      value: data.storeAddress || '-',
      icon: <MapPin size={18} />,
    },
    {
      id: 6,
      label: '서류 제출일',
      value: formatDate(data.createdAt),
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
