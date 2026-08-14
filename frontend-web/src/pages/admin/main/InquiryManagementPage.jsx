import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import InquirySummaryCards from '../../../components/admin/inquiry/InquirySummaryCards';
import InquiryTableList from '../../../components/admin/inquiry/InquiryTableList';
import { inquiryApi } from '../../../api/admin/inquiryApi';

const Container = styled.div`
  padding: 32px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  background-color: #f9fafb;
  min-height: 100vh;
`;

export default function AdminInquiryManagementPage() {
  const [inquiries, setInquiries] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [pageInfo, setPageInfo] = useState({
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
  });

  // 문의 목록 불러오기
  const fetchInquiries = async (page = 0) => {
    try {
      setIsLoading(true);
      const response = await inquiryApi.getInquiryList(page, pageInfo.size);

      if (response.data.success) {
        const { content, totalElements, totalPages, number } =
          response.data.data;
        setInquiries(content || []);
        setPageInfo((prev) => ({
          ...prev,
          page: number,
          totalElements,
          totalPages,
        }));
      }
    } catch (error) {
      console.error('문의 목록을 불러오는 중 오류 발생:', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchInquiries(0);
  }, []);

  return (
    <Container>
      {/* 요약 카드 컴포넌트 */}
      <InquirySummaryCards totalCount={pageInfo.totalElements} />

      {/* 문의 테이블 리스트 컴포넌트 */}
      <InquiryTableList
        inquiries={inquiries}
        isLoading={isLoading}
        pageInfo={pageInfo}
        onPageChange={(newPage) => fetchInquiries(newPage)}
      />
    </Container>
  );
}
