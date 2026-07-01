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

  const fetchCustomers = async () => {
    try {
      setLoading(true);
      const response = await customerApi.getCustomers();

      // 백엔드 구조에 맞춰 response.data.success 체크
      if (response?.data?.success) {
        console.log('백엔드 실 데이터 내용:', response.data.data.content);

        // 💡 핵심 수정: response.data.content가 아니라 response.data.data.content 입니다!
        setCustomers(response.data.data.content || []);
      }
    } catch (error) {
      console.error('고객 목록 로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCustomers();
  }, []);

  // 어떤 상황에서도 에러가 나지 않도록 배열 보장
  const safeCustomers = customers || [];

  const stats = {
    totalCount: safeCustomers.length,
    vipCount: safeCustomers.filter(
      (c) => c.customerType === 'REGULAR' || c.customerType === 'NORMAL',
    ).length,
    newCount: safeCustomers.filter((c) => c.customerType === 'NEW').length,
    favCount: safeCustomers.filter((c) => c.favorite).length,
    alertCount: safeCustomers.filter((c) => c.chatParticipant).length,
    totalSales: (
      safeCustomers.reduce((sum, c) => sum + (c.totalOrderAmount || 0), 0) /
      10000
    ).toFixed(0),
  };

  // 💡 핵심 수정: customers 대신 안전이 보장된 safeCustomers 기반으로 필터링 진행
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
