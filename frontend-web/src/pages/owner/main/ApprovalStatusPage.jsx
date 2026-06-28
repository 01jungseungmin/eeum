import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { Send } from 'lucide-react';
import AuthStatusBanner from '../../../components/owner/approval/AuthStatusBanner';
import BusinessInfoBox from '../../../components/owner/approval/BusinessInfoBox';
import RejectReasonBox from '../../../components/owner/approval/RejectReasonBox';
import InspectionChecklist from '../../../components/owner/approval/InspectionChecklist';

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

  // 조건 충족 시 빛나는 이음의 시그니처 그린 컬러
  background-color: #00a651;
  color: white;

  &:hover {
    background-color: #008c43;
  }

  // 비활성화(조건 미달 혹은 심사 중) 시 정갈한 그레이톤 처리
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
  const [status, setStatus] = useState('PENDING');
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

  const currentChecklist = checklist || {
    businessVerified: false,
    storeInfoCompleted: false,
    menuRegistered: false,
    businessHoursSet: false,
    settlementAccountRegistered: false,
    allCompleted: false,
    approvalStatus: 'PENDING',
    rejectionReason: null,
    reviewRequestedAt: null, // 기본값 방어
  };

  // 6개가 다 차고, 백엔드에 진짜 심사 신청 접수 일시(reviewRequestedAt)가 등록되어 있다면
  // 사장님이 '진짜로 신청을 완료한 상태'로 판단합니다.
  const isAlreadySubmitted =
    currentChecklist.allCompleted &&
    currentChecklist.reviewRequestedAt !== null;

  // 버튼 비활성화 규칙 수정
  const isButtonDisabled =
    !currentChecklist.allCompleted || // 1) 6개 항목 중 미완성된 게 있거나
    isAlreadySubmitted || // 2) ✨ 이미 신청 완료해서 심사 대기 중이거나
    status === 'APPROVED' || // 3) 최종 승인 완료되었거나
    submitting; // 4) 현재 누르는 중일 때 잠금

  // 상황별 버튼 텍스트 정밀 매칭
  const getButtonText = () => {
    if (submitting) return '신청 중...';
    if (status === 'APPROVED') return '승인 완료';
    if (isAlreadySubmitted) return '심사 대기 중'; // ✨ 진짜 신청 완료된 경우 대기 중으로 변환!
    return '입점 심사 신청하기'; // 항목은 다 채웠으나 아직 버튼을 안 눌렀거나 반려(REJECTED)당해 날짜가 날아갔을 때
  };

  const handleApplyApproval = async () => {
    if (!currentChecklist.allCompleted) {
      return alert('아직 완료되지 않은 필수 심사 항목이 있습니다.');
    }

    if (!window.confirm('입점 심사를 요청하시겠습니까?')) {
      return;
    }

    setSubmitting(true);
    try {
      const response = await approvalApi.applyOwnerStoreApproval();

      if (response.data.success) {
        alert('입점 심사 요청이 성공적으로 완료되었습니다! 🎉');

        fetchChecklistData();
      } else {
        alert(`요청 실패: ${response.data.message || '오류가 발생했습니다.'}`);
      }
    } catch (error) {
      console.error('입점 심사 요청 에러:', error);
      alert('서버 통신 중 에러가 발생했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleItemClick = (modalType) => {
    if (modalType === 'LICENSE' || modalType === 'BUSINESS_PROOF') {
      alert('사업자 등록증 인증은 본인인증 완료 시 자동으로 처리됩니다. 📋');
      return;
    }

    setActiveModal(modalType);
  };

  if (loading)
    return (
      <Container style={{ padding: '40px', textAlign: 'center' }}>
        데이터를 불러오는 중입니다...
      </Container>
    );

  return (
    <Container>
      {/* 사장 인증 상태 */}
      <AuthStatusBanner status={status} />

      {/* 사장 기본 정보 */}
      <BusinessInfoBox />

      {status === 'REJECTED' && (
        <RejectReasonBox reason={currentChecklist.rejectionReason} />
      )}

      <div style={{ marginTop: '32px' }}>
        <InspectionChecklist
          checklist={currentChecklist}
          onItemClick={handleItemClick}
        >
          <ApplySubmitButton
            disabled={isButtonDisabled}
            onClick={handleApplyApproval}
          >
            <Send size={16} />
            {getButtonText()}
          </ApplySubmitButton>
        </InspectionChecklist>
      </div>

      {activeModal === 'STORE_INFO' && (
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
