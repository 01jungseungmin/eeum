import { useState } from 'react';
import styled from 'styled-components';
import { RefreshCw } from 'lucide-react';
import { favoriteApi } from '../../../api/admin/favoriteApi';
import {
  FAVORITE_RECALCULATE_OPTIONS,
  FAVORITE_REF_TYPE_LABEL,
} from '../../../constants/favoriteConstants';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 20px 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
`;

const TitleGroup = styled.div`
  h3 {
    margin: 0 0 4px 0;
    font-size: 15px;
    font-weight: 700;
    color: #262626;
  }
  p {
    margin: 0;
    font-size: 12px;
    color: #8c8c8c;
  }
`;

const Controls = styled.div`
  display: flex;
  gap: 8px;
`;

const Select = styled.select`
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 0 14px;
  height: 40px;
  font-size: 13px;
  background: white;
  cursor: pointer;
`;

const RunButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 16px;
  border-radius: 8px;
  border: none;
  background: #2d5a43;
  color: white;
  font-size: 13px;
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

const ResultBox = styled.div`
  padding: 12px 16px;
  border-radius: 10px;
  background: #edf5f1;
  color: #2d5a43;
  font-size: 13px;
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
`;

function FavoriteRecalculatePanel() {
  const [target, setTarget] = useState('');
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState(null);

  const handleRun = async () => {
    const targetLabel = target
      ? FAVORITE_RECALCULATE_OPTIONS.find((o) => o.value === target)?.label
      : '전체';
    if (
      !window.confirm(
        `[${targetLabel}] 찜 카운트를 재계산할까요? 찜 쓰기가 한산한 시간대에 실행하는 걸 권장합니다.`,
      )
    ) {
      return;
    }

    try {
      setRunning(true);
      setResult(null);
      const res = await favoriteApi.recalculate(target || undefined);
      if (res.data?.success) {
        setResult(res.data.data);
      }
    } catch (error) {
      console.error('찜 카운트 재계산 실패:', error);
      alert(
        error.response?.data?.error?.message ||
          '재계산 중 오류가 발생했습니다.',
      );
    } finally {
      setRunning(false);
    }
  };

  return (
    <Card>
      <Header>
        <TitleGroup>
          <h3>favoriteCount 정합성 재계산</h3>
          <p>
            favorite 테이블 실제 row 수 기준으로 재계산합니다. 결과는 즉시
            반영돼요.
          </p>
        </TitleGroup>
        <Controls>
          <Select
            value={target}
            onChange={(e) => setTarget(e.target.value)}
          >
            {FAVORITE_RECALCULATE_OPTIONS.map((opt) => (
              <option
                key={opt.value}
                value={opt.value}
              >
                {opt.label}
              </option>
            ))}
          </Select>
          <RunButton
            onClick={handleRun}
            disabled={running}
          >
            <RefreshCw size={14} />
            {running ? '재계산 중...' : '재계산 실행'}
          </RunButton>
        </Controls>
      </Header>

      {result && (
        <ResultBox>
          <span>총 {result.totalUpdated.toLocaleString()}건 갱신</span>
          {Object.entries(result.updatedRows || {}).map(([type, count]) => (
            <span key={type}>
              {FAVORITE_REF_TYPE_LABEL[type] || type}: {count.toLocaleString()}
              건
            </span>
          ))}
        </ResultBox>
      )}
    </Card>
  );
}

export default FavoriteRecalculatePanel;
