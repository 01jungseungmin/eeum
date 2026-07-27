import React from 'react';
import styled from 'styled-components';
import InquiryItem from './InquiryItem';

const ListContainer = styled.div`
  display: flex;
  flex-direction: column;
  width: 100%;
`;

const EmptyState = styled.div`
  text-align: center;
  padding: 60px 20px;
  background: #fff;
  border: 1px solid #f1f3f5;
  border-radius: 16px;
  color: #868e96;
  font-size: 15px;
`;

export default function InquiryList({ data }) {
  if (!data || data.length === 0) {
    return <EmptyState>조건에 일치하는 문의 내역이 없습니다.</EmptyState>;
  }

  return (
    <ListContainer>
      {data.map((item) => (
        <InquiryItem key={item.inquiryId} item={item} />
      ))}
    </ListContainer>
  );
}
