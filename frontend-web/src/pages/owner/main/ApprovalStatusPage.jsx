import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { Send } from 'lucide-react';
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

const ChecklistHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  h3 {
    font-size: 18px;
    font-weight: 700;
    color: #262626;
    margin: 0;
  }
`;

const ApplySubmitButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;

  // 기본 활성화 (초록색)
  background-color: #00a651;
  color: white;

  &:hover {
    background-color: #008c43;
  }

  // 비활성화 상태 스타일 수정
  &:disabled {
    background-color: #f5f5f5;
    color: #bfbfbf;
    border: 1px solid #d9d9d9;
    cursor: not-allowed;
  }
`;

function ApprovalStatusPage() {
  const [checklist, setChecklist] = useState(null);
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState('REJECTED');
  const [activeModal, setActiveModal] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const fetchChecklistData = async () => {
    try {
      setLoading(true);
      const response = await approvalApi.getOwnerStoreChecklist();

      if (response.data.success) {
        const data = response.data.data;
        setChecklist(data);
        setStatus(data.approvalStatus);
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

  const handleApplyApproval = async () => {
    if (!currentChecklist.allCompleted) {
      return alert('아직 완료되지 않은 필수 심사 항목이 있습니다.');
    }

    if (
      !window.confirm(
        '입점 심사를 요청하시겠습니까? 서류 검토에는 수일이 소요될 수 있습니다.',
      )
    ) {
      return;
    }

    setSubmitting(true);
    try {
      const response = await approvalApi.applyOwnerStoreApproval();

      if (response.data.success) {
        alert('입점 심사 요청이 관리자에게 성공적으로 전달되었습니다!');
        fetchChecklistData();
      } else {
        alert(`요청 실패: ${response.data.message || '오류가 발생했습니다.'}`);
      }
    } catch (error) {
      console.error('입점 심사 요청 에러:', error);
      const serverMessage = error.response?.data?.message;
      alert(
        serverMessage
          ? `E러: ${serverMessage}`
          : '서버와 통신 중 에러가 발생했습니다.',
      );
    } finally {
      setSubmitting(false);
    }
  };

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
    businessVerified: false,
    storeInfoCompleted: false,
    menuRegistered: false,
    businessHoursSet: false,
    settlementAccountRegistered: false,
    allCompleted: false,
    approvalStatus: status,
    rejectionReason: '제출 서류 및 상점 정보를 확인해 주세요.',
  };

  // 💡 버튼 비활성화 규칙 정교화:
  // 1) 필수 항목이 미완료(allCompleted === false)이거나
  // 2) 이미 심사 중(PENDING)이거나 승인 완료(APPROVED)인 상태이거나
  // 3) 통신 중일 때 비활성화 처리합니다.
  const isButtonDisabled =
    !currentChecklist.allCompleted ||
    status === 'PENDING' ||
    status === 'APPROVED' ||
    submitting;

  // 💡 상태별 버튼 텍스트 동적 정의
  const getButtonText = () => {
    if (submitting) return '신청 중...';
    if (status === 'PENDING') return '심사 대기 중';
    if (status === 'APPROVED') return '승인 완료';
    return '입점 심사 신청하기';
  };

  return (
    <Container>
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

      <div style={{ marginTop: '32px' }}>
        <ChecklistHeader>
          <h3>입점 심사 체크리스트</h3>

          {/* 💡 무조건 렌더링하되, 상황에 따라 자물쇠(disabled)를 채웁니다. */}
          <ApplySubmitButton
            disabled={isButtonDisabled}
            onClick={handleApplyApproval}
          >
            <Send size={16} />
            {getButtonText()}
          </ApplySubmitButton>
        </ChecklistHeader>

        <InspectionChecklist
          checklist={currentChecklist}
          onItemClick={(modalType) => setActiveModal(modalType)}
        />
      </div>

      <InspectionTimeline status={status} />

      {activeModal === 'INFO' && (
        <BusinessInfoModal
          onClose={() => setActiveModal(null)}
          onSuccess={() => {
            setActiveModal(null);
            fetchChecklistData();
          }}
        />
      )}
      {activeModal === 'HOURS' && (
        <BusinessHoursModal
          onClose={() => setActiveModal(null)}
          onSuccess={() => {
            setActiveModal(null);
            fetchChecklistData();
          }}
        />
      )}
      {activeModal === 'MENU' && (
        <RepresentativeMenuModal
          onClose={() => setActiveModal(null)}
          onSuccess={() => {
            setActiveModal(null);
            fetchChecklistData();
          }}
        />
      )}
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

export default ApprovalStatusPage;
