import { useState } from 'react';
import styled from 'styled-components';
import { PencilLine } from 'lucide-react';
import { aiManagerApi } from '../../../../api/owner/aiManagerApi';
import {
  OWNER_METRIC_GROUPS,
  OWNER_METRICS,
} from '../../../../constants/aiOwnerMetrics';

const SectionCard = styled.div`
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const TitleArea = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;

  h3 {
    font-size: 15px;
    font-weight: 700;
    margin: 0;
    display: flex;
    align-items: center;
    gap: 6px;
    color: #111827;
  }

  span.desc {
    font-size: 12px;
    color: #6b7280;
    line-height: 1.5;
  }
`;

const FormRow = styled.form`
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: ${({ $grow }) => ($grow ? '1 1 200px' : '0 1 160px')};
  min-width: 0;

  label {
    font-size: 12px;
    font-weight: 600;
    color: #374151;
  }
`;

const controlStyle = `
  height: 38px;
  padding: 0 10px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 13px;
  color: #111827;
  background: #ffffff;
  outline: none;
  width: 100%;
  box-sizing: border-box;

  &:focus {
    border-color: #10b981;
  }
`;

const Select = styled.select`
  ${controlStyle}
`;

const Input = styled.input`
  ${controlStyle}
`;

const ValueBox = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const Unit = styled.span`
  font-size: 13px;
  color: #6b7280;
  white-space: nowrap;
`;

const SaveButton = styled.button`
  height: 38px;
  padding: 0 18px;
  border: none;
  border-radius: 8px;
  background: #10b981;
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;

  &:hover:not(:disabled) {
    background: #059669;
  }
  &:disabled {
    background: #d1d5db;
    cursor: not-allowed;
  }
`;

const Feedback = styled.div`
  font-size: 12.5px;
  font-weight: 600;
  color: ${({ $error }) => ($error ? '#dc2626' : '#16a34a')};
`;

const currentYearMonth = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
};

export default function OwnerMetricInputCard({ onSaved }) {
  const [thisMonth] = useState(currentYearMonth);
  const [metricType, setMetricType] = useState(OWNER_METRICS[0].type);
  const [yearMonth, setYearMonth] = useState(thisMonth);
  const [value, setValue] = useState('');
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState(null);

  const metric = OWNER_METRICS.find((m) => m.type === metricType);
  const numericValue = Number(value);
  const isValueValid =
    value !== '' &&
    Number.isFinite(numericValue) &&
    numericValue >= 0 &&
    (!metric.integer || Number.isInteger(numericValue)) &&
    (metric.max === undefined || numericValue <= metric.max);
  const canSubmit = isValueValid && Boolean(yearMonth) && !saving;

  let valueError = null;
  if (value !== '' && !isValueValid) {
    if (metric.integer && !Number.isInteger(numericValue)) {
      valueError = '정수로 입력해 주세요.';
    } else if (metric.max !== undefined && numericValue > metric.max) {
      valueError = `${metric.max}${metric.unit} 이하로 입력해 주세요.`;
    } else {
      valueError = '0 이상의 숫자를 입력해 주세요.';
    }
  }

  const handleChange = (setter) => (e) => {
    setter(e.target.value);
    setFeedback(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!canSubmit) return;

    setSaving(true);
    setFeedback(null);
    try {
      const res = await aiManagerApi.submitActualPowerUsage({
        metricType,
        value: numericValue,
        yearMonth,
      });
      if (!res.data?.success) {
        setFeedback({
          error: true,
          text: res.data?.message || '저장에 실패했어요.',
        });
        return;
      }
      setValue('');
      setFeedback({
        error: false,
        text: `${yearMonth} ${metric.label}을(를) 저장했어요. 분석에 반영했어요.`,
      });
      await onSaved?.();
    } catch (err) {
      setFeedback({
        error: true,
        text:
          err.response?.data?.error?.message ||
          '저장 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요.',
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <SectionCard>
      <TitleArea>
        <h3>
          <PencilLine
            size={16}
            color="#10b981"
          />{' '}
          우리 가게 실측값 입력
        </h3>
        <span className="desc">
          고지서·계량기 값을 입력하면 추정치 대신 사장님 입력값으로 분석해요.
          같은 연월을 다시 입력하면 값이 갱신돼요.
        </span>
      </TitleArea>

      <FormRow onSubmit={handleSubmit}>
        <Field $grow>
          <label htmlFor="metric-type">항목</label>
          <Select
            id="metric-type"
            value={metricType}
            onChange={handleChange(setMetricType)}
          >
            {OWNER_METRIC_GROUPS.map((group) => (
              <optgroup
                key={group.label}
                label={group.label}
              >
                {group.metrics.map((m) => (
                  <option
                    key={m.type}
                    value={m.type}
                  >
                    {m.label}
                  </option>
                ))}
              </optgroup>
            ))}
          </Select>
        </Field>

        <Field>
          <label htmlFor="metric-month">대상 연월</label>
          <Input
            id="metric-month"
            type="month"
            value={yearMonth}
            max={thisMonth}
            onChange={handleChange(setYearMonth)}
          />
        </Field>

        <Field>
          <label htmlFor="metric-value">값</label>
          <ValueBox>
            <Input
              id="metric-value"
              type="number"
              inputMode="decimal"
              min={0}
              max={metric.max}
              step={metric.integer ? 1 : 'any'}
              placeholder="0"
              value={value}
              onChange={handleChange(setValue)}
            />
            <Unit>{metric.unit}</Unit>
          </ValueBox>
        </Field>

        <SaveButton
          type="submit"
          disabled={!canSubmit}
        >
          {saving ? '저장 중...' : '저장'}
        </SaveButton>
      </FormRow>

      {valueError && <Feedback $error>{valueError}</Feedback>}
      {!valueError && feedback && (
        <Feedback $error={feedback.error}>{feedback.text}</Feedback>
      )}
    </SectionCard>
  );
}
