import { useEffect, useState } from 'react';
import styled from 'styled-components';
import AuthStatusBanner from '../../../components/owner/approval/AuthStatusBanner';
import BusinessInfoBox from '../../../components/owner/approval/BusinessInfoBox';
import RejectReasonBox from '../../../components/owner/approval/RejectReasonBox';
import InspectionChecklist from '../../../components/owner/approval/InspectionChecklist';
import InspectionTimeline from '../../../components/owner/approval/Inspectiontimeline';

import BusinessHoursModal from '../../../components/owner/approval/modals/BusinessHoursModal';
import BusinessInfoModal from '../../../components/owner/approval/modals/BusinessInfoModal';
import RepresentativeMenuModal from '../../../components/owner/approval/modals/RepresentativeMenuModal';
import SettlementAccountModal from '../../../components/owner/approval/modals/SettlementAccountModal';

import { approvalApi } from '../../../api/owner/ApprovalApi';

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
  const [checklist, setChecklist] = useState(null);
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState('REJECTED');

  // 모달 활성화 타겟 관리 상태 ('INFO', 'HOURS', 'MENU', 'ACCOUNT' 등)
  const [activeModal, setActiveModal] = useState(null);

  // 체크리스트 상태 API 동기화 함수
  const fetchChecklistData = async () => {
    try {
      setLoading(true);
      const response = await approvalApi.getOwnerStoreChecklist();

      if (response.data.success) {
        const data = response.data.data;
        setChecklist(data);
        setStatus(data.approvalStatus); // 실제 백엔드 승인 상태 동기화 ('PENDING', 'APPROVED', 'REJECTED')
      }
    } catch (error) {
      console.error('입점 심사 체크리스트 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchChecklistData();
  }, []);

  const handleStatusChange = (newStatus) => {
    setStatus(newStatus);
  };

  if (loading)
    return (
      <Container style={{ padding: '40px', textAlign: 'center' }}>
        데이터를 불러오는 중입니다...
      </Container>
    );

  const currentChecklist = checklist || {
    businessVerified: true,
    storeInfoCompleted: true,
    menuRegistered: false,
    businessHoursSet: false,
    settlementAccountRegistered: false,
    allCompleted: false,
    approvalStatus: status,
    rejectionReason: '제출 서류 및 상점 정보를 확인해 주세요.',
  };

  return (
    <Container>
      {/* 상태 변경용 미리보기 상단 가이드 */}
      <FilterSection>
        <span>미리보기:</span>
        <FilterBadge
          $active={status === 'PENDING'}
          onClick={() => handleStatusChange('PENDING')}
        >
          심사중
        </FilterBadge>
        <FilterBadge
          $active={status === 'APPROVED'}
          onClick={() => handleStatusChange('APPROVED')}
        >
          승인완료
        </FilterBadge>
        <FilterBadge
          $active={status === 'REJECTED'}
          $color="#ff4d4f"
          onClick={() => handleStatusChange('REJECTED')}
        >
          반려
        </FilterBadge>
      </FilterSection>

      <AuthStatusBanner status={status} />
      <BusinessInfoBox />

      {status === 'REJECTED' && (
        <RejectReasonBox reason={currentChecklist.rejectionReason} />
      )}

      {/* 💡 각 항목 클릭 시 설정해둔 고유 모달 키를 activeModal 상태로 변경하도록 연결 */}
      <InspectionChecklist
        checklist={currentChecklist}
        onItemClick={(modalType) => setActiveModal(modalType)}
      />

      <InspectionTimeline status={status} />

      {/* 상점 기본 정보 입력/수정 모달 */}
      {activeModal === 'INFO' && (
        <BusinessInfoModal
          onClose={() => setActiveModal(null)}
          onSuccess={() => {
            setActiveModal(null);
            fetchChecklistData();
          }}
        />
      )}

      {/* 대표 메뉴 등록 모달 */}
      {activeModal === 'MENU' && (
        <RepresentativeMenuModal
          onClose={() => setActiveModal(null)}
          onSuccess={() => {
            setActiveModal(null);
            fetchChecklistData();
          }}
        />
      )}

      {/* 영업시간 설정 모달 */}
      {activeModal === 'HOURS' && (
        <BusinessHoursModal
          onClose={() => setActiveModal(null)}
          onSuccess={() => {
            setActiveModal(null);
            fetchChecklistData();
          }}
        />
      )}

      {/* 정산 계좌 등록 모달 */}
      {activeModal === 'ACCOUNT' && (
        <SettlementAccountModal
          onClose={() => setActiveModal(null)}
          onSuccess={() => {
            setActiveModal(null);
            fetchChecklistData();
          }}
        />
      )}
    </Container>
  );
}

export default ApprovalStatus;
