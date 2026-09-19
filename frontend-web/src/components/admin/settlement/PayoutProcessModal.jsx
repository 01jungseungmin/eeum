import { useCallback, useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
import { X, RefreshCw } from 'lucide-react';
import { settlementApi } from '../../../api/admin/settlementApi';
import { PAYMENT_CANCELLATION_STATUS_LABEL } from '../../../constants/settlementConstants';

const Overlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
`;

const ModalBox = styled.div`
  background: #ffffff;
  width: 100%;
  max-width: 560px;
  max-height: 85vh;
  overflow-y: auto;
  border-radius: 16px;
  padding: 28px;
  box-shadow:
    0 20px 25px -5px rgba(0, 0, 0, 0.1),
    0 8px 10px -6px rgba(0, 0, 0, 0.1);
  position: relative;
  box-sizing: border-box;
`;

const CloseButton = styled.button`
  position: absolute;
  top: 24px;
  right: 24px;
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
`;

const Title = styled.h2`
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 6px 0;
`;

const Subtitle = styled.p`
  font-size: 13px;
  color: #64748b;
  margin: 0 0 24px 0;
`;

const StatusBox = styled.div`
  padding: 14px 16px;
  border-radius: 10px;
  font-size: 13px;
  margin-bottom: 20px;

  &.loading {
    background: #f5f5f5;
    color: #595959;
  }
  &.success {
    background: #edf5f1;
    color: #2d5a43;
  }
  &.error {
    background: #fff1f0;
    color: #cf1322;
  }
`;

const BlockerSection = styled.div`
  margin-bottom: 20px;

  h3 {
    font-size: 14px;
    font-weight: 700;
    color: #262626;
    margin: 0 0 12px 0;
  }
`;

const BlockerCard = styled.div`
  border: 1px solid #ffccc7;
  background: #fff8f7;
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 10px;
  font-size: 12px;

  .row {
    display: flex;
    justify-content: space-between;
    margin-bottom: 4px;
    color: #595959;
  }
  .row strong {
    color: #262626;
  }
  .reason {
    color: #cf1322;
    margin: 6px 0 10px;
  }
`;

const ButtonRow = styled.div`
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
`;

const SmallButton = styled.button`
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid #d9d9d9;
  background: white;
  font-size: 12px;
  font-weight: 600;
  color: #262626;
  cursor: pointer;

  &:hover {
    background: #f5f5f5;
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const PartialForm = styled.div`
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #ffccc7;
  display: flex;
  flex-direction: column;
  gap: 8px;

  label {
    font-size: 11px;
    color: #8c8c8c;
  }
  input {
    width: 100%;
    box-sizing: border-box;
    padding: 6px 10px;
    border-radius: 6px;
    border: 1px solid #e0e0e0;
    font-size: 12px;
  }
`;

const FormGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;

  label {
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
  }
  input {
    width: 100%;
    box-sizing: border-box;
    height: 44px;
    background-color: #f4f4f5;
    border: 1px solid #e4e4e7;
    border-radius: 10px;
    padding: 0 14px;
    font-size: 14px;
  }
`;

const SubmitButton = styled.button`
  width: 100%;
  padding: 12px 0;
  border-radius: 10px;
  border: none;
  background: #2d5a43;
  color: white;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;

  &:hover {
    background: #244a37;
  }
  &:disabled {
    background: #bfbfbf;
    cursor: not-allowed;
  }
`;

const RetryButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid #2d5a43;
  background: white;
  color: #2d5a43;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
`;

const won = (value) => `${Math.round(Number(value || 0)).toLocaleString()}원`;

function PayoutProcessModal({ settlement, onClose, onCompleted }) {
  const [claimToken, setClaimToken] = useState(null);
  const [claiming, setClaiming] = useState(false);
  const [blockers, setBlockers] = useState([]);
  const [error, setError] = useState(null);
  const [payoutReference, setPayoutReference] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [partialFormOrderId, setPartialFormOrderId] = useState(null);
  const [partialForm, setPartialForm] = useState({
    cumulativeCancelledAmount: '',
    pgFeeRate: '',
    platformFeeRate: '',
  });

  const id = settlement.weeklySettlementId;
  // StrictMode 등으로 인한 중복 claim 요청 방지용 Lock (AuthContext.restoreSession과 동일한 패턴)
  const hasClaimedRef = useRef(false);

  const loadBlockers = useCallback(async () => {
    try {
      const res = await settlementApi.getBlockingCancellations(id);
      if (res.data?.success) {
        setBlockers(res.data.data || []);
      }
    } catch (err) {
      console.error('차단 사유 조회 실패:', err);
    }
  }, [id]);

  const attemptClaim = useCallback(async () => {
    setClaiming(true);
    setError(null);
    try {
      const res = await settlementApi.claimPayout(id);
      if (res.data?.success) {
        setClaimToken(res.data.data);
        setBlockers([]);
      }
    } catch (err) {
      const code = err.response?.data?.error?.code;
      const message =
        err.response?.data?.error?.message || '지급 선점에 실패했습니다.';
      setError(message);
      if (code === 'SETTLEMENT_006') {
        loadBlockers();
      }
    } finally {
      setClaiming(false);
    }
  }, [id, loadBlockers]);

  useEffect(() => {
    if (hasClaimedRef.current) return;
    hasClaimedRef.current = true;
    queueMicrotask(() => attemptClaim());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleReconcileFull = async (orderId) => {
    try {
      await settlementApi.reconcileConfirmedCancellation(id, orderId);
      alert('전액 취소 내부 반영을 재시도했습니다.');
      if (claimToken) {
        loadBlockers();
      } else {
        attemptClaim();
      }
    } catch (err) {
      alert(
        err.response?.data?.error?.message ||
          '재시도 처리 중 오류가 발생했습니다.',
      );
    }
  };

  const handleReconcilePartial = async (orderId) => {
    const { cumulativeCancelledAmount, pgFeeRate, platformFeeRate } =
      partialForm;
    if (!cumulativeCancelledAmount || !pgFeeRate || !platformFeeRate) {
      alert('누적 취소액, PG 수수료율, 플랫폼 수수료율을 모두 입력해주세요.');
      return;
    }
    try {
      await settlementApi.reconcilePartialCancellation(id, orderId, {
        cumulativeCancelledAmount: Number(cumulativeCancelledAmount),
        pgFeeRate: Number(pgFeeRate),
        platformFeeRate: Number(platformFeeRate),
      });
      alert('부분 취소 대사를 반영했습니다.');
      setPartialFormOrderId(null);
      setPartialForm({
        cumulativeCancelledAmount: '',
        pgFeeRate: '',
        platformFeeRate: '',
      });
      if (claimToken) {
        loadBlockers();
      } else {
        attemptClaim();
      }
    } catch (err) {
      alert(
        err.response?.data?.error?.message ||
          '부분 취소 대사 처리 중 오류가 발생했습니다.',
      );
    }
  };

  const handleComplete = async () => {
    if (!payoutReference.trim()) {
      alert('지급 참조 번호(이체 내역 등)를 입력해주세요.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await settlementApi.completePayout(id, {
        claimToken,
        payoutReference: payoutReference.trim(),
      });
      alert('지급 완료 처리되었습니다.');
      onCompleted();
    } catch (err) {
      const code = err.response?.data?.error?.code;
      const message =
        err.response?.data?.error?.message || '완료 처리에 실패했습니다.';
      setError(message);
      if (code === 'SETTLEMENT_006') {
        loadBlockers();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Overlay onClick={onClose}>
      <ModalBox onClick={(e) => e.stopPropagation()}>
        <CloseButton onClick={onClose}>
          <X size={20} />
        </CloseButton>
        <Title>정산 지급 처리</Title>
        <Subtitle>
          매장 #{settlement.storeId} · {won(settlement.payoutAmount)}
        </Subtitle>

        {claiming && (
          <StatusBox className="loading">지급 작업을 선점하는 중...</StatusBox>
        )}

        {!claiming && error && !claimToken && (
          <StatusBox className="error">{error}</StatusBox>
        )}

        {blockers.length > 0 && (
          <BlockerSection>
            <h3>지급을 막고 있는 취소 작업 ({blockers.length}건)</h3>
            {blockers.map((b) => (
              <BlockerCard key={b.orderId}>
                <div className="row">
                  <span>주문번호</span>
                  <strong>{b.orderNumber}</strong>
                </div>
                <div className="row">
                  <span>상태</span>
                  <strong>
                    {PAYMENT_CANCELLATION_STATUS_LABEL[b.status] || b.status}
                  </strong>
                </div>
                {b.failureReason && (
                  <div className="reason">
                    {b.failureCode}: {b.failureReason}
                  </div>
                )}

                <ButtonRow>
                  <SmallButton onClick={() => handleReconcileFull(b.orderId)}>
                    전액 취소 재시도
                  </SmallButton>
                  <SmallButton
                    onClick={() =>
                      setPartialFormOrderId(
                        partialFormOrderId === b.orderId ? null : b.orderId,
                      )
                    }
                  >
                    부분 취소 대사
                  </SmallButton>
                </ButtonRow>

                {partialFormOrderId === b.orderId && (
                  <PartialForm>
                    <label>누적 취소 금액 (원)</label>
                    <input
                      type="number"
                      value={partialForm.cumulativeCancelledAmount}
                      onChange={(e) =>
                        setPartialForm((prev) => ({
                          ...prev,
                          cumulativeCancelledAmount: e.target.value,
                        }))
                      }
                    />
                    <label>PG 수수료율 (예: 0.015)</label>
                    <input
                      type="number"
                      step="0.001"
                      value={partialForm.pgFeeRate}
                      onChange={(e) =>
                        setPartialForm((prev) => ({
                          ...prev,
                          pgFeeRate: e.target.value,
                        }))
                      }
                    />
                    <label>플랫폼 수수료율 (예: 0.015)</label>
                    <input
                      type="number"
                      step="0.001"
                      value={partialForm.platformFeeRate}
                      onChange={(e) =>
                        setPartialForm((prev) => ({
                          ...prev,
                          platformFeeRate: e.target.value,
                        }))
                      }
                    />
                    <SmallButton
                      onClick={() => handleReconcilePartial(b.orderId)}
                    >
                      대사 반영
                    </SmallButton>
                  </PartialForm>
                )}
              </BlockerCard>
            ))}

            <RetryButton onClick={attemptClaim}>
              <RefreshCw size={14} />
              해소 후 다시 시도
            </RetryButton>
          </BlockerSection>
        )}

        {claimToken && (
          <>
            <StatusBox className="success">
              지급 작업을 선점했습니다. 실제 계좌 이체 후 참조 번호를 입력하고
              완료 처리해주세요.
            </StatusBox>
            <FormGroup>
              <label>지급 참조 번호</label>
              <input
                type="text"
                placeholder="예: 은행 이체 승인번호"
                value={payoutReference}
                onChange={(e) => setPayoutReference(e.target.value)}
              />
            </FormGroup>
            <SubmitButton
              onClick={handleComplete}
              disabled={submitting}
            >
              {submitting ? '처리 중...' : '완료 처리'}
            </SubmitButton>
          </>
        )}
      </ModalBox>
    </Overlay>
  );
}

export default PayoutProcessModal;
