import { useCallback, useEffect, useState } from 'react';
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

const PAGE_SIZE = 10;
// 백엔드 InquiryStatus (domain/inquiry/enums/InquiryStatus.java)
const STATUSES = ['PENDING', 'ANSWERED', 'CLOSED'];

export default function AdminInquiryManagementPage() {
  const [inquiries, setInquiries] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  // 상단 카드와 탭이 함께 쓰는 상태 필터: ALL | PENDING | ANSWERED | CLOSED
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [counts, setCounts] = useState(null);
  const [pageInfo, setPageInfo] = useState({
    page: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
  });

  // 문의 목록 불러오기
  const fetchInquiries = useCallback(async (page, status) => {
    try {
      setIsLoading(true);
      const response = await inquiryApi.getInquiryList(
        page,
        PAGE_SIZE,
        status === 'ALL' ? {} : { status },
      );

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
  }, []);

  // 카드에 보여줄 상태별 전체 건수 — 건수만 필요해서 1건씩 요청하고 totalElements 를 쓴다
  const fetchCounts = useCallback(async () => {
    const results = await Promise.allSettled(
      STATUSES.map((status) => inquiryApi.getInquiryList(0, 1, { status })),
    );

    setCounts(
      Object.fromEntries(
        STATUSES.map((status, index) => [
          status,
          results[index].status === 'fulfilled'
            ? (results[index].value.data?.data?.totalElements ?? 0)
            : 0,
        ]),
      ),
    );
  }, []);

  useEffect(() => {
    queueMicrotask(() => fetchInquiries(0, statusFilter));
  }, [statusFilter, fetchInquiries]);

  useEffect(() => {
    queueMicrotask(() => fetchCounts());
  }, [fetchCounts]);

  return (
    <Container>
      {/* 요약 카드 컴포넌트 */}
      <InquirySummaryCards
        counts={counts}
        selectedStatus={statusFilter}
        onSelect={setStatusFilter}
      />

      {/* 문의 테이블 리스트 컴포넌트 */}
      <InquiryTableList
        inquiries={inquiries}
        isLoading={isLoading}
        pageInfo={pageInfo}
        onPageChange={(newPage) => fetchInquiries(newPage, statusFilter)}
        statusFilter={statusFilter}
        onStatusChange={setStatusFilter}
      />
    </Container>
  );
}
