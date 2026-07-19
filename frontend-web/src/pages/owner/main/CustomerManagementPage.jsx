import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { customerApi } from '../../../api/owner/customerApi';
import StatsCardGrid from '../../../components/owner/customer/StatsCardGrid';
import ControlBar from '../../../components/owner/customer/ControlBar';
import CustomerRow from '../../../components/owner/customer/CustomerRow';

const Container = styled.div`
  padding: 24px;
  background-color: #f8fafc;
  min-height: 100vh;
  font-family: sans-serif;
  color: #334155;
`;

const MainBoard = styled.div`
  background-color: #ffffff;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  overflow: hidden;
`;
const TableContainer = styled.div`
  overflow-x: auto;
`;
const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  text-align: left;
`;
const Th = styled.th`
  background-color: #f8fafc;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 600;
  padding: 12px 16px;
  border-bottom: 1px solid #f1f5f9;
`;
const EmptyTd = styled.td`
  padding: 40px;
  text-align: center;
  color: #94a3b8;
`;

export default function CustomerManagementPage() {
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeFilter, setActiveFilter] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  // 💡 요약 통계 데이터를 저장할 상태 추가
  const [summary, setSummary] = useState({
    totalCustomerCount: 0,
    regularCustomerCount: 0,
    normalCustomerCount: 0,
    newCustomerCount: 0,
    potentialCustomerCount: 0,
    favoriteCustomerCount: 0,
    chatParticipantCustomerCount: 0,
    totalSalesAmount: 0,
  });

  const fetchData = async () => {
    try {
      setLoading(true);

      // 두 API를 병렬(Promise.all)로 호출하여 지연 속도 최적화
      const [customersResponse, summaryResponse] = await Promise.all([
        customerApi.getCustomers(),
        customerApi.getCustomerSummary(),
      ]);

      // 고객 목록 데이터 셋팅
      if (customersResponse?.data?.success) {
        setCustomers(customersResponse.data.data.content || []);
      }

      // 요약 통계 데이터 셋팅
      if (summaryResponse?.data?.success) {
        setSummary(summaryResponse.data.data);
      }
    } catch (error) {
      console.error('고객 관리 데이터 로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  // 💡 API 명세를 기반으로 가공된 대시보드 stats 구조 매핑
  const stats = {
    // 전체 고객 수
    totalCount: summary.totalCustomerCount,
    // 단골 고객 수: regularCustomerCount + normalCustomerCount
    vipCount: summary.regularCustomerCount + summary.normalCustomerCount,
    // 신규 고객 수
    newCount: summary.newCustomerCount,
    // 즐겨찾기 수
    favCount: summary.favoriteCustomerCount,
    // 알림 신청 수 (채팅 참여자 수)
    alertCount: summary.chatParticipantCustomerCount,
    // 총 매출액 (만원 단위 변환)
    totalSales: summary.totalSalesAmount
      ? (Number(summary.totalSalesAmount) / 10000).toFixed(0)
      : '0',
  };

  const safeCustomers = customers || [];

  // 클라이언트 사이드 검색 및 탭 필터링 로직
  const filteredCustomers = safeCustomers.filter((customer) => {
    const nameMatch = customer.maskedName?.includes(searchTerm);
    const phoneMatch = customer.maskedPhone?.includes(searchTerm);
    if (!nameMatch && !phoneMatch) return false;

    switch (activeFilter) {
      case 'VIP':
        return (
          customer.customerType === 'REGULAR' ||
          customer.customerType === 'NORMAL'
        );
      case 'NEW':
        return customer.customerType === 'NEW';
      case 'FAV_ALERT':
        return customer.favorite && customer.chatParticipant;
      case 'FAV':
        return customer.favorite;
      case 'ALERT':
        return customer.chatParticipant;
      default:
        return true;
    }
  });

  return (
    <Container>
      <StatsCardGrid stats={stats} />

      <MainBoard>
        <ControlBar
          searchTerm={searchTerm}
          setSearchTerm={setSearchTerm}
          activeFilter={activeFilter}
          setActiveFilter={setActiveFilter}
          totalCount={stats.totalCount}
        />

        <TableContainer>
          <Table>
            <thead>
              <tr>
                <Th style={{ paddingLeft: '24px' }}>고객</Th>
                <Th>등급 / 관심유형</Th>
                <Th style={{ textAlign: 'center' }}>총 주문</Th>
                <Th style={{ textAlign: 'right' }}>누적 금액</Th>
                <Th style={{ textAlign: 'center' }}>리뷰 수 (평점)</Th>
                <Th style={{ textAlign: 'center' }}>최근 주문</Th>
                <Th style={{ paddingRight: '24px', textAlign: 'center' }}>
                  상세
                </Th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <EmptyTd colSpan={7}>데이터를 불러오는 중입니다...</EmptyTd>
                </tr>
              ) : filteredCustomers.length > 0 ? (
                filteredCustomers.map((customer) => (
                  <CustomerRow key={customer.customerId} customer={customer} />
                ))
              ) : (
                <tr>
                  <EmptyTd colSpan={7}>조건에 맞는 고객이 없습니다.</EmptyTd>
                </tr>
              )}
            </tbody>
          </Table>
        </TableContainer>
      </MainBoard>
    </Container>
  );
}
