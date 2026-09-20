import styled from 'styled-components';
import {
  FAVORITE_REF_TYPE_TABS,
  FAVORITE_STAT_LIMIT_OPTIONS,
} from '../../../constants/favoriteConstants';

const Row = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 20px;
`;

const TabGroup = styled.div`
  display: flex;
  gap: 8px;
`;

const TabButton = styled.button`
  padding: 8px 16px;
  border-radius: 999px;
  border: 1px solid ${(props) => (props.$active ? '#2d5a43' : '#e0e0e0')};
  background: ${(props) => (props.$active ? '#2d5a43' : 'white')};
  color: ${(props) => (props.$active ? 'white' : '#595959')};
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    border-color: #2d5a43;
  }
`;

const DateInput = styled.input`
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 0 12px;
  height: 40px;
  font-size: 13px;
  color: #262626;
  background: white;
`;

const LimitSelect = styled.select`
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 0 14px;
  height: 40px;
  font-size: 13px;
  background: white;
  cursor: pointer;
`;

const DateSeparator = styled.span`
  color: #bfbfbf;
  font-size: 13px;
`;

function FavoriteStatFilterBar({
  refType,
  onRefTypeChange,
  from,
  onFromChange,
  to,
  onToChange,
  limit,
  onLimitChange,
}) {
  return (
    <Row>
      <TabGroup>
        {FAVORITE_REF_TYPE_TABS.map((tab) => (
          <TabButton
            key={tab.value}
            $active={refType === tab.value}
            onClick={() => onRefTypeChange(tab.value)}
          >
            {tab.label}
          </TabButton>
        ))}
      </TabGroup>

      <DateInput
        type="date"
        value={from}
        onChange={(e) => onFromChange(e.target.value)}
      />
      <DateSeparator>~</DateSeparator>
      <DateInput
        type="date"
        value={to}
        onChange={(e) => onToChange(e.target.value)}
      />

      <LimitSelect
        value={limit}
        onChange={(e) => onLimitChange(Number(e.target.value))}
      >
        {FAVORITE_STAT_LIMIT_OPTIONS.map((n) => (
          <option
            key={n}
            value={n}
          >
            상위 {n}개
          </option>
        ))}
      </LimitSelect>
    </Row>
  );
}

export default FavoriteStatFilterBar;
