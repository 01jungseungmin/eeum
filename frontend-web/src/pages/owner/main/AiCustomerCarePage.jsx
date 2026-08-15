import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import styled from 'styled-components';

import AiCareDetailCard from '../../../components/owner/ai/AiCareDetailCard';
import AiMessageDraftModal from '../../../components/owner/ai/AiMessageDraftModal';

import { aiCustomerCareApi } from '../../../api/owner/aiCustomerCareApi';
import { useAiDraft } from '../../../hooks/useAiDraft';

const PageContainer = styled.div`
  padding: 24px;
  background: #f8f9fa;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 16px;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
`;

const PageHeader = styled.div`
  h1 {
    margin: 0 0 6px 0;
    font-size: 20px;
    font-weight: bold;
    color: #1a1f2c;
  }
  p {
    margin: 0;
    font-size: 13px;
    color: #8e94a0;
  }
`;

const StateBox = styled.div`
  background: white;
  border: 1px solid #eef0f2;
  border-radius: 16px;
  padding: 60px 0;
  text-align: center;
  font-size: 14px;
  color: #8e94a0;
`;

function AiCustomerCarePage() {
  const [searchParams] = useSearchParams();
  const highlightedCareType = searchParams.get('careType');

  const [cards, setCards] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // 초안 생성 요청을 훅에 넘겨 AI_015(초안 보관 초과) 재확인 흐름을 공용 처리
  const createDraftRequest = useCallback(
    ({ careType, ...draftData }) =>
      aiCustomerCareApi.createDraft(careType, draftData),
    [],
  );
  const { draft, setDraft, creating, createDraft } =
    useAiDraft(createDraftRequest);

  const loadCards = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await aiCustomerCareApi.getCareCards();
      if (response.data.success) {
        setCards(response.data.data ?? []);
      }
    } catch (err) {
      console.error('AI 고객 케어 카드 조회 실패:', err);
      setError('고객 케어 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCards();
  }, [loadCards]);

  const handleCreateDraft = (careType, draftData) =>
    createDraft({ careType, ...draftData });

  // 발송/예약 후에는 대상 고객 수와 제외 인원이 달라지므로 카드를 다시 불러온다
  const handleUpdated = () => {
    loadCards();
  };

  return (
    <PageContainer>
      <PageHeader>
        <h1>AI 고객 케어</h1>
        <p>
          지금 말을 걸어야 할 고객을 AI가 골라뒀어요. 문구를 확인하고 보내보세요.
        </p>
      </PageHeader>

      {loading && <StateBox>고객 케어 정보를 불러오는 중이에요...</StateBox>}
      {!loading && error && <StateBox>{error}</StateBox>}
      {!loading && !error && cards.length === 0 && (
        <StateBox>아직 케어가 필요한 고객이 없어요</StateBox>
      )}

      {!loading &&
        !error &&
        cards.map((card) => (
          <AiCareDetailCard
            key={card.careType}
            card={card}
            highlighted={card.careType === highlightedCareType}
            creating={creating}
            onCreateDraft={handleCreateDraft}
          />
        ))}

      {draft && (
        <AiMessageDraftModal
          message={draft}
          onClose={() => setDraft(null)}
          onUpdated={handleUpdated}
        />
      )}
    </PageContainer>
  );
}

export default AiCustomerCarePage;
