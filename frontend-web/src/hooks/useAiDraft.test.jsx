import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';

import { useAiDraft } from './useAiDraft';

const draftResponse = (id) => ({ data: { data: { messageId: id } } });

// 백엔드 공통 에러 응답 형태
const errorResponse = (code, message, data) => ({
  response: { data: { error: { code, message }, data } },
});

describe('useAiDraft', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.spyOn(window, 'alert').mockImplementation(() => {});
    vi.spyOn(window, 'confirm').mockImplementation(() => true);
  });

  it('초안 생성에 성공하면 draft 에 보관하고 값을 반환한다', async () => {
    const request = vi.fn().mockResolvedValue(draftResponse(1));
    const { result } = renderHook(() => useAiDraft(request));

    let returned;
    await act(async () => {
      returned = await result.current.createDraft({ careType: 'CART_INTEREST' });
    });

    expect(request).toHaveBeenCalledWith({
      careType: 'CART_INTEREST',
      confirmDelete: false,
    });
    expect(returned).toEqual({ messageId: 1 });
    expect(result.current.draft).toEqual({ messageId: 1 });
    expect(result.current.creating).toBe(false);
  });

  it('요청 중에는 creating 이 true 가 된다', async () => {
    let resolveRequest;
    const request = vi.fn(
      () => new Promise((resolve) => (resolveRequest = resolve)),
    );
    const { result } = renderHook(() => useAiDraft(request));

    act(() => {
      result.current.createDraft();
    });
    await waitFor(() => expect(result.current.creating).toBe(true));

    await act(async () => {
      resolveRequest(draftResponse(2));
    });
    expect(result.current.creating).toBe(false);
  });

  it('AI_015(보관 개수 초과) 시 확인을 받고 confirmDelete=true 로 재요청한다', async () => {
    const request = vi
      .fn()
      .mockRejectedValueOnce(
        errorResponse('AI_015', '보관 중인 초안이 가득 찼어요.', {
          currentCount: 5,
          limit: 5,
        }),
      )
      .mockResolvedValueOnce(draftResponse(3));
    const { result } = renderHook(() => useAiDraft(request));

    let returned;
    await act(async () => {
      returned = await result.current.createDraft({ channel: 'APP_PUSH' });
    });

    expect(window.confirm).toHaveBeenCalledWith(
      '보관 중인 초안이 가득 찼어요.\n(보관 중 5개 / 최대 5개)',
    );
    expect(request).toHaveBeenLastCalledWith({
      channel: 'APP_PUSH',
      confirmDelete: true,
    });
    expect(returned).toEqual({ messageId: 3 });
    expect(result.current.draft).toEqual({ messageId: 3 });
  });

  it('AI_015 확인 창에서 취소하면 재요청 없이 null 을 반환한다', async () => {
    window.confirm.mockReturnValue(false);
    const request = vi
      .fn()
      .mockRejectedValue(errorResponse('AI_015', '가득 찼어요.', null));
    const { result } = renderHook(() => useAiDraft(request));

    let returned;
    await act(async () => {
      returned = await result.current.createDraft();
    });

    expect(request).toHaveBeenCalledTimes(1);
    expect(returned).toBeNull();
    expect(result.current.draft).toBeNull();
  });

  it('그 밖의 실패는 백엔드 문구를 그대로 alert 하고 null 을 반환한다', async () => {
    const request = vi
      .fn()
      .mockRejectedValue(
        errorResponse('AI_PLAN_REQUIRED', 'AI 플랜 가입이 필요해요.'),
      );
    const { result } = renderHook(() => useAiDraft(request));

    let returned;
    await act(async () => {
      returned = await result.current.createDraft();
    });

    expect(window.alert).toHaveBeenCalledWith('AI 플랜 가입이 필요해요.');
    expect(returned).toBeNull();
    expect(result.current.creating).toBe(false);
  });

  it('응답 본문이 없는 실패에는 기본 문구를 노출한다', async () => {
    const request = vi.fn().mockRejectedValue(new Error('Network Error'));
    const { result } = renderHook(() => useAiDraft(request));

    await act(async () => {
      await result.current.createDraft();
    });

    expect(window.alert).toHaveBeenCalledWith(
      '초안 생성에 실패했어요. 잠시 후 다시 시도해주세요.',
    );
  });
});
