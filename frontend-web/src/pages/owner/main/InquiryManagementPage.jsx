import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import SummaryCards from '../../../components/owner/inquiry/SummaryCards';
import FilterBar from '../../../components/owner/inquiry/FilterBar';
import InquiryList from '../../../components/owner/inquiry/InquiryList';
import { inquiryApi } from '../../../api/owner/inquiryApi';

const Container = styled.div`
  flex: 1;
  padding: 20px;
  background-color: #f8f9fa;
  min-height: 100vh;
  font-family: 'Noto Sans KR', sans-serif;
`;

const LoadingText = styled.div`
  text-align: center;
  padding: 40px;
  font-size: 16px;
  color: #666;
`;

const STATUS_MAP = {
  PENDING: '미답변',
  ANSWERED: '답변완료',
};

const CATEGORY_MAP = {
  STORE: '상품 문의',
  ORDER: '주문 문의',
  RESERVATION: '예약 문의',
  PAYMENT: '결제 문의',
  ETC: '기타',
};

export default function InquiryManagement() {
  const [inquiries, setInquiries] = useState([]);
  const [loading, setLoading] = useState(true);

  // 필터 상태 관리
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('전체');
  const [typeFilter, setTypeFilter] = useState('전체 유형');

  // 공통 목록 및 데이터 로드 함수
  const loadInquiries = async () => {
    try {
      const res = await inquiryApi.getStoreInquiries({ page: 0, size: 50 });
      // 기존에 쓰시던 res.data.data.content 완벽 반영
      if (res.data.success && res.data.data.content) {
        setInquiries(res.data.data.content);
      }
    } catch (error) {
      console.error('문의 목록 로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadInquiries();
  }, []);

  // 백엔드 데이터를 기반으로 동적 필터링을 수행하는 로직
  const filteredInquiries = inquiries.filter((item) => {
    const mappedStatus = STATUS_MAP[item.status] || '미답변';
    const matchesStatus =
      statusFilter === '전체' || mappedStatus === statusFilter;

    const mappedType = CATEGORY_MAP[item.category] || '기타';
    const matchesType = typeFilter === '전체 유형' || mappedType === typeFilter;

    const matchesSearch =
      item.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.writerName.toLowerCase().includes(searchTerm.toLowerCase());

    return matchesStatus && matchesType && matchesSearch;
  });

  // 카운트 자동 계산 데이터 바인딩
  const counts = {
    total: inquiries.length,
    pending: inquiries.filter((i) => i.status === 'PENDING').length,
    completed: inquiries.filter((i) => i.status !== 'PENDING').length,
  };

  return (
    <Container>
      {/* 요약 카드 대시보드 */}
      <SummaryCards
        total={counts.total}
        pending={counts.pending}
        completed={counts.completed}
        currentStatus={statusFilter}
        setStatusFilter={setStatusFilter}
      />

      {/* 필터 바 */}
      <FilterBar
        searchTerm={searchTerm}
        setSearchTerm={setSearchTerm}
        statusFilter={statusFilter}
        setStatusFilter={setStatusFilter}
        typeFilter={typeFilter}
        setTypeFilter={setTypeFilter}
        counts={counts}
      />

      {/* 리스트 본문 컨테이너 */}
      {loading ? (
        <LoadingText>문의 내역을 불러오는 중입니다...</LoadingText>
      ) : (
        <InquiryList data={filteredInquiries} onRefresh={loadInquiries} />
      )}
    </Container>
  );
}
