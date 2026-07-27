import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import SummaryCards from '../../../components/owner/report/SummartCards';
import NoticeBanner from '../../../components/owner/report/NoticeBanner';
import FilterSection from '../../../components/owner/report/FilterSection';
import ReportItem from '../../../components/owner/report/ReportItem';
import ReportDetailModal from '../../../components/owner/report/ReportDetailModal';
import { reportApi } from '../../../api/owner/reportApi';

const Container = styled.div`
  padding: 24px;
  background-color: #f8fafc;
`;

const EmptyState = styled.div`
  text-align: center;
  padding: 40px;
  color: #94a3b8;
  font-size: 14px;
`;

export default function ReportManagement() {
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);

  // 필터 상태
  const [selectedStatus, setSelectedStatus] = useState('ALL');
  const [selectedType, setSelectedType] = useState('ALL');
  const [searchKeyword, setSearchKeyword] = useState('');

  // 모달 상태 및 상세 데이터
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [detailData, setDetailData] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);

  useEffect(() => {
    fetchReports();
  }, []);

  const fetchReports = async () => {
    try {
      setLoading(true);
      const res = await reportApi.getReports({ page: 0, size: 50 });
      if (res.data && res.data.success) {
        console.log('신고 목록:', res.data.data.content);
        setReports(res.data.data.content || []);
      }
    } catch (error) {
      console.error('신고 목록을 가져오는데 실패했습니다:', error);
    } finally {
      setLoading(false);
    }
  };

  // 상세 모달 열기 핸들러
  const handleOpenDetail = async (reportId) => {
    setIsModalOpen(true);
    setDetailLoading(true);
    try {
      const res = await reportApi.getReportDetail(reportId);
      if (res.data && res.data.success) {
        console.log('상세 신고 데이터:', res.data.data);
        setDetailData(res.data.data);
      }
    } catch (error) {
      console.error('상세 정보를 불러오는 중 오류 발생:', error);
    } finally {
      setDetailLoading(false);
    }
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setDetailData(null);
  };

  // 필터링 적용 목록
  const filteredReports = reports.filter((item) => {
    const matchStatus =
      selectedStatus === 'ALL' || item.status === selectedStatus;
    const matchType =
      selectedType === 'ALL' || item.targetType === selectedType;
    const matchSearch =
      (item.content && item.content.includes(searchKeyword)) ||
      (item.reporterName && item.reporterName.includes(searchKeyword));

    return matchStatus && matchType && matchSearch;
  });

  return (
    <Container>
      <SummaryCards reports={reports} />
      <NoticeBanner />
      <FilterSection
        selectedStatus={selectedStatus}
        onStatusChange={setSelectedStatus}
        selectedType={selectedType}
        onTypeChange={setSelectedType}
        searchKeyword={searchKeyword}
        onSearchChange={setSearchKeyword}
      />

      {loading ? (
        <EmptyState>데이터를 불러오는 중입니다...</EmptyState>
      ) : filteredReports.length === 0 ? (
        <EmptyState>조건에 맞는 신고 내역이 없습니다.</EmptyState>
      ) : (
        filteredReports.map((item) => (
          <ReportItem
            key={item.reportId}
            item={item}
            onOpenDetail={handleOpenDetail}
          />
        ))
      )}

      {/* 모달 출력 */}
      {isModalOpen && (
        <ReportDetailModal
          data={detailData}
          loading={detailLoading}
          onClose={handleCloseModal}
        />
      )}
    </Container>
  );
}
