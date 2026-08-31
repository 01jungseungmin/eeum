import { useState, useCallback } from 'react';

// 초안 보관 개수 캡 초과 시 백엔드가 409(AI_015)와 함께
// AiDraftCapacityExceededResponseDto 를 data 로 내려준다.
const DRAFT_LIMIT_CODE = 'AI_015';

// AI 초안 생성 API를 감싸는 공용 훅.
// - AI_015(초안 보관 개수 초과) 시 사장님 확인 후 confirmDelete=true 로 재요청
// - AI_PLAN_REQUIRED / AI_USAGE_LIMIT_EXCEEDED 등 그 외 실패는 백엔드 문구를 그대로 노출
// - 생성된 메시지를 draft 로 보관해 AiMessageDraftModal 에 그대로 넘길 수 있게 한다
export const useAiDraft = (createDraftRequest) => {
  const [draft, setDraft] = useState(null);
  const [creating, setCreating] = useState(false);

  const createDraft = useCallback(
    async (payload = {}) => {
      setCreating(true);

      try {
        const response = await createDraftRequest({
          ...payload,
          confirmDelete: false,
        });
        setDraft(response.data.data);
        return response.data.data;
      } catch (error) {
        const body = error.response?.data;

        if (body?.error?.code === DRAFT_LIMIT_CODE) {
          const capacity = body.data;
          const detail = capacity
            ? `\n(보관 중 ${capacity.currentCount}개 / 최대 ${capacity.limit}개)`
            : '';

          if (window.confirm(`${body.error.message}${detail}`)) {
            const retried = await createDraftRequest({
              ...payload,
              confirmDelete: true,
            });
            setDraft(retried.data.data);
            return retried.data.data;
          }
          return null;
        }

        console.error('AI 초안 생성 실패:', error);
        alert(
          body?.error?.message ??
            '초안 생성에 실패했어요. 잠시 후 다시 시도해주세요.',
        );
        return null;
      } finally {
        setCreating(false);
      }
    },
    [createDraftRequest],
  );

  return { draft, setDraft, creating, createDraft };
};
