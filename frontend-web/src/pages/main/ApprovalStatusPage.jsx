import styled from 'styled-components';
import TopNavbar from '../../layouts/TopNavbar';
import { useState } from 'react';
import AuthStatusBanner from '../../components/approval/AuthStatusBanner';
import BusinessInfoBox from '../../components/approval/BusinessInfoBox';
import RejectReasonBox from '../../components/approval/RejectReasonBox';
import InspectionChecklist from '../../components/approval/InspectionChecklist';
import InspectionTimeline from '../../components/approval/Inspectiontimeline';

const Container = styled.div`
  margin: 0 auto;
`;

const FilterSection = styled.div`
  display: flex;
  gap: 10px;
  margin-bottom: 24px;
  align-items: center;
  span {
    font-size: 14px;
    color: #666;
    margin-right: 10px;
  }
`;

const FilterBadge = styled.div`
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  background-color: ${(props) =>
    props.$active ? props.$color || '#00a651' : '#fff'};
  color: ${(props) => (props.$active ? '#fff' : '#666')};
  border: 1px solid ${(props) => (props.$active ? 'transparent' : '#eee')};
`;

function ApprovalStatus() {
  const [status, setStatus] = useState('REJECTED');
  return (
    <Container>
      <FilterSection>
        <span>미리보기:</span>
        <FilterBadge
          $active={status === 'REVIEWING'}
          onClick={() => setStatus('REVIEWING')}
        >
          심사중
        </FilterBadge>
        <FilterBadge
          $active={status === 'APPROVED'}
          onClick={() => setStatus('APPROVED')}
        >
          승인완료
        </FilterBadge>
        <FilterBadge
          $active={status === 'REJECTED'}
          $color="#ff4d4f"
          onClick={() => setStatus('REJECTED')}
        >
          반려
        </FilterBadge>
      </FilterSection>

      {/* 사업자 인증 상태 */}
      <AuthStatusBanner status={status} />

      {/* 사업자 정보 */}
      <BusinessInfoBox />

      {/* 반려 사유 */}
      {status === 'REJECTED' && <RejectReasonBox />}

      {/* 심사 체크리스트 */}
      <InspectionChecklist />

      {/* 심사 타임라인 */}
      <InspectionTimeline />
    </Container>
  );
}

export default ApprovalStatus;
