import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';

import AiMessageRow from '../../../components/owner/ai/AiMessageRow';
import AiMessageDraftModal from '../../../components/owner/ai/AiMessageDraftModal';

import { aiMessageApi } from '../../../api/owner/aiMessageApi';
import { AI_MESSAGE_TYPE_TABS } from '../../../constants/aiConstants';

const PAGE_SIZE = 20;

const PageContainer = styled.div`
  padding: 24px;
  background: #f8f9fa;
  min-height: 100vh;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
`;

const MainCard = styled.div`
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  border: 1px solid #eef0f2;
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  h2 {
    font-size: 15px;
    font-weight: bold;
    color: #1a1f2c;
    margin: 0;
  }
  .total {
    font-size: 13px;
    color: #8e94a0;
  }
`;

const FilterTabGroup = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
`;

const FilterButton = styled.button`
  background: ${(props) => (props.$active ? '#1a1f2c' : '#ffffff')};
  color: ${(props) => (props.$active ? '#ffffff' : '#8e94a0')};
  border: 1px solid ${(props) => (props.$active ? '#1a1f2c' : '#eef0f2')};
  padding: 8px 14px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s;
`;

const MessageList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const StateBox = styled.div`
  padding: 60px 0;
  text-align: center;
  font-size: 14px;
  color: #8e94a0;
`;

const PaginationWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
`;

const PageButton = styled.button`
  padding: 6px 12px;
  font-size: 13px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  color: #4a5568;
  cursor: pointer;

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
`;

const PageInfoText = styled.span`
  font-size: 13px;
  color: #8e94a0;
`;

function AiMessagePage() {
  const [messages, setMessages] = useState([]);
  const [pageInfo, setPageInfo] = useState({
    page: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
  });
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [selectedMessage, setSelectedMessage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const loadMessages = useCallback(async (page, type) => {
    setLoading(true);
    setError(null);

    try {
      const response = await aiMessageApi.getMessages(page, PAGE_SIZE, type);
      if (response.data.success) {
        const { content, totalElements, totalPages, number } =
          response.data.data;

        setMessages(content ?? []);
        setPageInfo((prev) => ({
          ...prev,
          page: number,
          totalElements,
          totalPages,
        }));
      }
    } catch (err) {
      console.error('AI 생성 메시지 목록 조회 실패:', err);
      setError('메시지 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadMessages(0, typeFilter);
  }, [loadMessages, typeFilter]);

  // 발송/예약/취소 후 현재 페이지를 다시 불러와 상태 배지를 갱신
  const handleUpdated = () => {
    loadMessages(pageInfo.page, typeFilter);
  };

  return (
    <PageContainer>
      <MainCard>
        <CardHeader>
          <h2>AI 생성 메시지</h2>
          <span className="total">총 {pageInfo.totalElements}건</span>
        </CardHeader>

        <FilterTabGroup>
          {AI_MESSAGE_TYPE_TABS.map((tab) => (
            <FilterButton
              key={tab.value}
              type="button"
              $active={typeFilter === tab.value}
              onClick={() => setTypeFilter(tab.value)}
            >
              {tab.label}
            </FilterButton>
          ))}
        </FilterTabGroup>

        {loading && <StateBox>메시지를 불러오는 중이에요...</StateBox>}
        {!loading && error && <StateBox>{error}</StateBox>}
        {!loading && !error && messages.length === 0 && (
          <StateBox>아직 AI가 생성한 메시지가 없어요</StateBox>
        )}

        {!loading && !error && messages.length > 0 && (
          <MessageList>
            {messages.map((message) => (
              <AiMessageRow
                key={message.messageId}
                message={message}
                onClick={setSelectedMessage}
              />
            ))}
          </MessageList>
        )}

        {pageInfo.totalPages > 1 && (
          <PaginationWrapper>
            <PageButton
              type="button"
              disabled={pageInfo.page === 0}
              onClick={() => loadMessages(pageInfo.page - 1, typeFilter)}
            >
              이전
            </PageButton>
            <PageInfoText>
              {pageInfo.page + 1} / {pageInfo.totalPages}
            </PageInfoText>
            <PageButton
              type="button"
              disabled={pageInfo.page + 1 >= pageInfo.totalPages}
              onClick={() => loadMessages(pageInfo.page + 1, typeFilter)}
            >
              다음
            </PageButton>
          </PaginationWrapper>
        )}
      </MainCard>

      {selectedMessage && (
        <AiMessageDraftModal
          message={selectedMessage}
          onClose={() => setSelectedMessage(null)}
          onUpdated={handleUpdated}
        />
      )}
    </PageContainer>
  );
}

export default AiMessagePage;
