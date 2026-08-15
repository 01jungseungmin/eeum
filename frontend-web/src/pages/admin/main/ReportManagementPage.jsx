import React, { useState, useEffect, useCallback, useMemo } from 'react';
import styled from 'styled-components';
import ReportStatCards from '../../../components/admin/report/ReportStatCards';
import ReportList from '../../../components/admin/report/ReportList';
import { reportApi } from '../../../api/admin/reportApi';

const Container = styled.div`
  margin: 0 auto;
  padding: 32px;
`;

const ReportManagementPage = () => {
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedStatus, setSelectedStatus] = useState('ALL');
  const [page, setPage] = useState(0);

  // API 호출: 파라미터 없이 전체 목록 1회 불러오기
  const fetchReports = useCallback(async () => {
    setLoading(true);
    try {
      const res = await reportApi.getReportList();
      const responseData = res.data;

      if (responseData.success) {
        // Response 구조에 맞춰 설정 (Page 객체 형태일 수도 있고 Array 형태일 수도 있음)
        const list = Array.isArray(responseData.data)
          ? responseData.data
          : responseData.data?.content || [];
        setReports(list);
      }
    } catch (error) {
      console.error('신고 목록을 불러오는데 실패했습니다.', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchReports();
  }, [fetchReports]);

  // 프론트엔드 상태(Status) 필터링
  const filteredReports = useMemo(() => {
    if (selectedStatus === 'ALL') {
      return reports;
    }
    return reports.filter((item) => item.status === selectedStatus);
  }, [reports, selectedStatus]);

  // 필터링된 전체 결과 기준으로 총 페이지 수 계산 (페이지당 10개 기준)
  const pageSize = 10;
  const totalPages = Math.ceil(filteredReports.length / pageSize);

  // 현재 페이지에 해당하는 데이터만 잘라내기 (클라이언트 페이징)
  const currentPageReports = useMemo(() => {
    const startIndex = page * pageSize;
    return filteredReports.slice(startIndex, startIndex + pageSize);
  }, [filteredReports, page, pageSize]);

  // 상태(Status) 변경 핸들러
  const handleStatusChange = (status) => {
    setSelectedStatus(status);
    setPage(0); // 필터 변경 시 첫 페이지로 리셋
  };

  return (
    <Container>
      {/* 요약 통계 카드 */}
      <ReportStatCards reports={reports} />

      {/* 필터링된 데이터 전달 */}
      <ReportList
        reports={currentPageReports}
        loading={loading}
        page={page}
        totalPages={totalPages}
        selectedStatus={selectedStatus}
        onStatusChange={handleStatusChange}
        onPageChange={setPage}
      />
    </Container>
  );
};

export default ReportManagementPage;
