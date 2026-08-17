import React, { useState } from 'react';
import styled from 'styled-components';
import { Plus, X } from 'lucide-react';

// --- Styled Components ---
const BottomSection = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #f1f5f9;
`;

const SectionTitle = styled.h2`
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 20px 0;
`;

const FilterGroup = styled.div`
  margin-bottom: 24px;

  &:last-child {
    margin-bottom: 0;
  }
`;

const SubLabel = styled.div`
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 12px;
`;

const TagContainer = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
`;

const Tag = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 13px;
  color: #334155;
  font-weight: 500;
`;

const RemoveButton = styled.button`
  background: none;
  border: none;
  color: #ef4444;
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
`;

const InputRow = styled.div`
  display: flex;
  gap: 10px;
`;

const Input = styled.input`
  flex: 1;
  background-color: #f1f5f9;
  border: none;
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 14px;

  &:focus {
    outline: 2px solid #00b074;
    background-color: #ffffff;
  }
`;

const AddWordButton = styled.button`
  display: flex;
  align-items: center;
  gap: 4px;
  background-color: #00b074;
  color: white;
  border: none;
  border-radius: 8px;
  padding: 0 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #009663;
  }
`;

const SettingBox = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #ffffff;
  border: 1px solid #f1f5f9;
  border-radius: 12px;
  padding: 14px 20px;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;

  &:last-child {
    margin-bottom: 0;
  }
`;

const ToggleContainer = styled.div`
  width: 44px;
  height: 24px;
  background-color: ${({ $active }) => ($active ? '#0f172a' : '#e2e8f0')};
  border-radius: 12px;
  padding: 2px;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: background-color 0.2s;
`;

const ToggleHandle = styled.div`
  width: 20px;
  height: 20px;
  background-color: white;
  border-radius: 50%;
  transform: ${({ $active }) =>
    $active ? 'translateX(20px)' : 'translateX(0)'};
  transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
`;

// --- Component ---
export default function AutoFilterSection() {
  const [bannedWords, setBannedWords] = useState([
    '사기',
    '도박',
    '불법',
    '비방',
  ]);
  const [newWord, setNewWord] = useState('');
  const [autoBlock, setAutoBlock] = useState(true);
  const [autoHide, setAutoHide] = useState(true);

  const handleAddWord = () => {
    if (newWord.trim() && !bannedWords.includes(newWord.trim())) {
      setBannedWords([...bannedWords, newWord.trim()]);
      setNewWord('');
    }
  };

  const handleRemoveWord = (wordToRemove) => {
    setBannedWords(bannedWords.filter((word) => word !== wordToRemove));
  };

  return (
    <BottomSection>
      <SectionTitle>공지사항 / 자동 필터</SectionTitle>

      {/* 금지어 관리 */}
      <FilterGroup>
        <SubLabel>금지어 관리</SubLabel>
        <TagContainer>
          {bannedWords.map((word) => (
            <Tag key={word}>
              <span>{word}</span>
              <RemoveButton onClick={() => handleRemoveWord(word)}>
                <X size={14} />
              </RemoveButton>
            </Tag>
          ))}
        </TagContainer>

        <InputRow>
          <Input
            type="text"
            placeholder="금지어 추가"
            value={newWord}
            onChange={(e) => setNewWord(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleAddWord()}
          />
          <AddWordButton onClick={handleAddWord}>
            <Plus size={16} />
            추가
          </AddWordButton>
        </InputRow>
      </FilterGroup>

      {/* 자동 신고 설정 */}
      <FilterGroup>
        <SubLabel>자동 신고 설정</SubLabel>
        <SettingBox>
          <span>중복 게시글 자동 차단</span>
          <ToggleContainer
            $active={autoBlock}
            onClick={() => setAutoBlock(!autoBlock)}
          >
            <ToggleHandle $active={autoBlock} />
          </ToggleContainer>
        </SettingBox>

        <SettingBox>
          <span>신고 3회 이상 게시글 자동 숨김</span>
          <ToggleContainer
            $active={autoHide}
            onClick={() => setAutoHide(!autoHide)}
          >
            <ToggleHandle $active={autoHide} />
          </ToggleContainer>
        </SettingBox>
      </FilterGroup>
    </BottomSection>
  );
}
