import styled from 'styled-components';
import {
  CHAT_ROOM_TYPE_FILTER_OPTIONS,
  CHAT_ROOM_ACTIVE_FILTER_OPTIONS,
} from '../../../constants/chatConstants';

const FilterRow = styled.div`
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
`;

const StatusSelect = styled.select`
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 0 14px;
  height: 40px;
  font-size: 14px;
  color: #262626;
  background: white;
  cursor: pointer;
`;

function ChatRoomFilterBar({ type, onTypeChange, isActive, onActiveChange }) {
  return (
    <FilterRow>
      <StatusSelect
        value={type}
        onChange={(e) => onTypeChange(e.target.value)}
      >
        {CHAT_ROOM_TYPE_FILTER_OPTIONS.map((opt) => (
          <option
            key={opt.value}
            value={opt.value}
          >
            {opt.label}
          </option>
        ))}
      </StatusSelect>

      <StatusSelect
        value={isActive}
        onChange={(e) => onActiveChange(e.target.value)}
      >
        {CHAT_ROOM_ACTIVE_FILTER_OPTIONS.map((opt) => (
          <option
            key={opt.value}
            value={opt.value}
          >
            {opt.label}
          </option>
        ))}
      </StatusSelect>
    </FilterRow>
  );
}

export default ChatRoomFilterBar;
