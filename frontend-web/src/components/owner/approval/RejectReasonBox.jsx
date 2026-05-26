import React from 'react';
import styled from 'styled-components';
import { AlertCircle, ChevronRight } from 'lucide-react';

const RejectReasonBox = () => {
  const reasons = [
    {
      id: 1,
      tag: '서류 문제',
      title:
        '제출된 사업자 등록증의 상호명이 입력하신 상점명과 일치하지 않습니다.',
      description:
        "사업자 등록증에 '맛있는반찬'으로 기재되어 있으나, 상점명은 '맛있는 반찬가게'로 입력되어 있습니다. 사업자 등록증 원본 또는 상점명을 일치시켜 재제출해주세요.",
      linkText: '사업자 등록증 재제출',
    },
    {
      id: 2,
      tag: '추가 안내',
      title: '상점 대표 이미지가 등록되어 있지 않습니다.',
      description:
        '고객이 상점을 더 잘 인식할 수 있도록 대표 이미지 1장 이상을 등록해주세요.',
      linkText: '상점 관리에서 이미지 등록',
    },
  ];

  return (
    <Container>
      <Header>
        <AlertCircle size={20} color="#ff4d4f" />
        <Title>반려 사유</Title>
      </Header>

      <ReasonList>
        {reasons.map((item) => (
          <ReasonCard key={item.id}>
            <NumberBadge>{item.id}</NumberBadge>
            <Content>
              <Tag>{item.tag}</Tag>
              <ReasonTitle>{item.title}</ReasonTitle>
              <Description>{item.description}</Description>
              <LinkButton>
                <ChevronRight size={16} />
                {item.linkText}
              </LinkButton>
            </Content>
          </ReasonCard>
        ))}
      </ReasonList>
    </Container>
  );
};

const Container = styled.div`
  background: #fffbfa;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #ffccc7;
  margin-bottom: 30px;
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
`;

const Title = styled.h3`
  font-size: 18px;
  font-weight: 700;
  color: #262626;
  margin: 0;
`;

const ReasonList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const ReasonCard = styled.div`
  background: white;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #fff1f0;
  display: flex;
  gap: 16px;
`;

const NumberBadge = styled.div`
  width: 24px;
  height: 24px;
  background-color: #ff4d4f;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: bold;
  flex-shrink: 0; // 숫자 배지 크기 고정
`;

const Content = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
`;

const Tag = styled.span`
  background: #fff1f0;
  color: #cf1322;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
`;

const ReasonTitle = styled.h4`
  font-size: 16px;
  font-weight: 700;
  color: #262626;
  margin: 4px 0;
  line-height: 1.4;
`;

const Description = styled.p`
  font-size: 14px;
  color: #595959;
  line-height: 1.6;
  margin: 0;
  word-break: keep-all;
`;

const LinkButton = styled.button`
  background: transparent;
  border: none;
  color: #389e0d; // 이미지의 초록색 링크 텍스트
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  margin-top: 8px;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
`;

export default RejectReasonBox;
