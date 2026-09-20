import { useState } from 'react';
import styled from 'styled-components';
import { settlementApi } from '../../../api/admin/settlementApi';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);

  h3 {
    margin: 0 0 4px 0;
    font-size: 15px;
    font-weight: 700;
    color: #262626;
  }
  p {
    margin: 0 0 16px 0;
    font-size: 12px;
    color: #8c8c8c;
    line-height: 1.6;
  }
`;

const FormRow = styled.form`
  display: flex;
  gap: 8px;

  input {
    flex: 1;
    padding: 10px 14px;
    border-radius: 8px;
    border: 1px solid #e0e0e0;
    font-size: 13px;
  }
`;

const SubmitButton = styled.button`
  padding: 10px 16px;
  border-radius: 8px;
  border: 1px solid #2d5a43;
  background: white;
  color: #2d5a43;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;

  &:hover {
    background: #edf5f1;
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

// 정산 기간 마감 이후 뒤늦게 지급 가능해진(늦게 완료 처리된 주문 등) 원장은 자동으로
// 어느 주차에도 섞이지 않고 운영 수습 대기열에 남는다. 이를 조회하는 목록 API가 아직
// 없어(운영 모니터링 영역에서 추가 예정), 지금은 ownerRevenueId를 직접 입력해 복구한다.
function LateRevenueRecoveryCard() {
  const [ownerRevenueId, setOwnerRevenueId] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    const id = ownerRevenueId.trim();
    if (!id) return;

    setSubmitting(true);
    try {
      await settlementApi.recoverLateRevenue(id);
      alert(`원장 #${id}을(를) 원래 주차로 재마감했습니다.`);
      setOwnerRevenueId('');
    } catch (error) {
      alert(
        error.response?.data?.error?.message ||
          '재마감 처리 중 오류가 발생했습니다.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card>
      <h3>지연 수익 복구</h3>
      <p>
        정산 마감 기간 밖으로 밀려나 어느 주차에도 포함되지 못한 수익
        원장(ownerRevenueId)을 원래 주차로 다시 마감합니다. 이미 지급 완료된
        주차는 변경되지 않습니다.
      </p>
      <FormRow onSubmit={handleSubmit}>
        <input
          type="number"
          placeholder="원장 ID (ownerRevenueId)"
          value={ownerRevenueId}
          onChange={(e) => setOwnerRevenueId(e.target.value)}
        />
        <SubmitButton
          type="submit"
          disabled={submitting}
        >
          {submitting ? '처리 중...' : '재마감'}
        </SubmitButton>
      </FormRow>
    </Card>
  );
}

export default LateRevenueRecoveryCard;
