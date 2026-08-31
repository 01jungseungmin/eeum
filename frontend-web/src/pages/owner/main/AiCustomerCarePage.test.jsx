import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

import AiCustomerCarePage from './AiCustomerCarePage';
import { aiCustomerCareApi } from '../../../api/owner/aiCustomerCareApi';

vi.mock('../../../api/owner/aiCustomerCareApi', () => ({
  aiCustomerCareApi: { getCareCards: vi.fn(), createDraft: vi.fn() },
}));

vi.mock('../../../components/owner/ai/AiMessageDraftModal', () => ({
  default: ({ message }) => <div>초안 모달: {message.title}</div>,
}));

const card = (overrides = {}) => ({
  careType: 'CART_INTEREST',
  title: '구매 관심이 높은 고객',
  targetCustomerCount: 12,
  reason: '장바구니에 담아두고 아직 안 산 고객이에요',
  preparedMessage: '오늘까지만 할인해요',
  recentlyNotifiedExcludedCount: 0,
  sendable: true,
  ...overrides,
});

const renderPage = (search = '') =>
  render(
    <MemoryRouter initialEntries={[`/ai-customer-care${search}`]}>
      <AiCustomerCarePage />
    </MemoryRouter>,
  );

describe('AiCustomerCarePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.spyOn(window, 'alert').mockImplementation(() => {});
  });

  it('케어 카드 목록을 렌더링한다', async () => {
    aiCustomerCareApi.getCareCards.mockResolvedValue({
      data: { success: true, data: [card(), card({ careType: 'INACTIVE_REGULAR', title: '한동안 방문이 없는 단골' })] },
    });

    renderPage();
    expect(
      screen.getByText('고객 케어 정보를 불러오는 중이에요...'),
    ).toBeInTheDocument();

    expect(await screen.findByText('구매 관심이 높은 고객')).toBeInTheDocument();
    expect(screen.getByText('한동안 방문이 없는 단골')).toBeInTheDocument();
  });

  it('대상 카드가 없으면 빈 상태 문구를 보여준다', async () => {
    aiCustomerCareApi.getCareCards.mockResolvedValue({
      data: { success: true, data: [] },
    });

    renderPage();

    expect(
      await screen.findByText('아직 케어가 필요한 고객이 없어요'),
    ).toBeInTheDocument();
  });

  it('조회에 실패하면 에러 문구를 보여준다', async () => {
    aiCustomerCareApi.getCareCards.mockRejectedValue(new Error('500'));

    renderPage();

    expect(
      await screen.findByText(
        '고객 케어 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.',
      ),
    ).toBeInTheDocument();
  });

  it('대상 고객이 없으면 초안 만들기 대신 안내 문구를 보여준다', async () => {
    aiCustomerCareApi.getCareCards.mockResolvedValue({
      data: { success: true, data: [card({ targetCustomerCount: 0 })] },
    });

    renderPage();

    expect(
      await screen.findByText('아직 이 조건에 해당하는 고객이 없어요.'),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /초안 만들기/ }),
    ).not.toBeInTheDocument();
  });

  it('발송 불가 카드는 별도 안내 문구를 보여준다', async () => {
    aiCustomerCareApi.getCareCards.mockResolvedValue({
      data: { success: true, data: [card({ sendable: false })] },
    });

    renderPage();

    expect(
      await screen.findByText('지금은 이 카드로 메시지를 보낼 수 없어요.'),
    ).toBeInTheDocument();
  });

  it('최근 알림으로 제외된 인원을 안내한다', async () => {
    aiCustomerCareApi.getCareCards.mockResolvedValue({
      data: { success: true, data: [card({ recentlyNotifiedExcludedCount: 3 })] },
    });

    renderPage();

    expect(
      await screen.findByText(/최근 7일 내 알림을 받은 3명은 제외했어요/),
    ).toBeInTheDocument();
  });

  it('초안을 만들면 선택한 채널·힌트로 요청하고 모달을 연다', async () => {
    aiCustomerCareApi.getCareCards.mockResolvedValue({
      data: { success: true, data: [card()] },
    });
    aiCustomerCareApi.createDraft.mockResolvedValue({
      data: { data: { messageId: 1, title: '생성된 초안' } },
    });

    renderPage();
    await screen.findByText('구매 관심이 높은 고객');

    await userEvent.selectOptions(
      screen.getByLabelText('발송 채널'),
      'KAKAO_ALERT',
    );
    await userEvent.type(
      screen.getByPlaceholderText(/문구에 반영할 힌트/),
      '김치찌개 세트',
    );
    await userEvent.click(screen.getByRole('button', { name: /초안 만들기/ }));

    await waitFor(() =>
      expect(aiCustomerCareApi.createDraft).toHaveBeenCalledWith(
        'CART_INTEREST',
        {
          channel: 'KAKAO_ALERT',
          contextHint: '김치찌개 세트',
          confirmDelete: false,
        },
      ),
    );
    expect(await screen.findByText('초안 모달: 생성된 초안')).toBeInTheDocument();
  });

  it('힌트를 비워두면 null 로 보낸다', async () => {
    aiCustomerCareApi.getCareCards.mockResolvedValue({
      data: { success: true, data: [card()] },
    });
    aiCustomerCareApi.createDraft.mockResolvedValue({
      data: { data: { messageId: 1, title: '초안' } },
    });

    renderPage();
    await screen.findByText('구매 관심이 높은 고객');

    await userEvent.click(screen.getByRole('button', { name: /초안 만들기/ }));

    await waitFor(() =>
      expect(aiCustomerCareApi.createDraft).toHaveBeenCalledWith(
        'CART_INTEREST',
        expect.objectContaining({ contextHint: null }),
      ),
    );
  });

  it('초안 생성에 실패하면 모달을 열지 않는다', async () => {
    aiCustomerCareApi.getCareCards.mockResolvedValue({
      data: { success: true, data: [card()] },
    });
    aiCustomerCareApi.createDraft.mockRejectedValue({
      response: { data: { error: { message: 'AI 플랜 가입이 필요해요.' } } },
    });

    renderPage();
    await screen.findByText('구매 관심이 높은 고객');

    await userEvent.click(screen.getByRole('button', { name: /초안 만들기/ }));

    await waitFor(() =>
      expect(window.alert).toHaveBeenCalledWith('AI 플랜 가입이 필요해요.'),
    );
    expect(screen.queryByText(/초안 모달/)).not.toBeInTheDocument();
  });
});
