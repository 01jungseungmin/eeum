import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import AiMessagePage from './AiMessagePage';
import { aiMessageApi } from '../../../api/owner/aiMessageApi';

vi.mock('../../../api/owner/aiMessageApi', () => ({
  aiMessageApi: { getMessages: vi.fn() },
}));

// 목록 모달은 별도 테스트가 있어 여기서는 열림 여부만 본다
vi.mock('../../../components/owner/ai/AiMessageDraftModal', () => ({
  default: ({ message }) => <div>초안 모달: {message.title}</div>,
}));

const message = (id, overrides = {}) => ({
  messageId: id,
  title: `메시지 ${id}`,
  content: '문구',
  type: 'CUSTOMER_CARE',
  channel: 'APP_PUSH',
  status: 'DRAFT',
  createdAt: '2026-08-15T10:00:00',
  ...overrides,
});

const pageResponse = (content, { number = 0, totalPages = 1 } = {}) => ({
  data: {
    success: true,
    data: { content, number, totalPages, totalElements: content.length },
  },
});

describe('AiMessagePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('조회 결과를 목록과 총 건수로 렌더링한다', async () => {
    aiMessageApi.getMessages.mockResolvedValue(
      pageResponse([message(1), message(2)]),
    );

    render(<AiMessagePage />);
    expect(screen.getByText('메시지를 불러오는 중이에요...')).toBeInTheDocument();

    expect(await screen.findByText('메시지 1')).toBeInTheDocument();
    expect(screen.getByText('메시지 2')).toBeInTheDocument();
    expect(screen.getByText('총 2건')).toBeInTheDocument();
  });

  it('결과가 없으면 빈 상태 문구를 보여준다', async () => {
    aiMessageApi.getMessages.mockResolvedValue(pageResponse([]));

    render(<AiMessagePage />);

    expect(
      await screen.findByText('아직 AI가 생성한 메시지가 없어요'),
    ).toBeInTheDocument();
  });

  it('조회에 실패하면 에러 문구를 보여준다', async () => {
    aiMessageApi.getMessages.mockRejectedValue(new Error('500'));

    render(<AiMessagePage />);

    expect(
      await screen.findByText(
        '메시지 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.',
      ),
    ).toBeInTheDocument();
  });

  it('첫 조회는 0페이지 / ALL 필터로 요청한다', async () => {
    aiMessageApi.getMessages.mockResolvedValue(pageResponse([message(1)]));

    render(<AiMessagePage />);

    await waitFor(() =>
      expect(aiMessageApi.getMessages).toHaveBeenCalledWith(0, 20, 'ALL'),
    );
  });

  it('유형 탭을 누르면 해당 유형으로 0페이지부터 다시 조회한다', async () => {
    aiMessageApi.getMessages.mockResolvedValue(
      pageResponse([message(1)], { number: 2, totalPages: 3 }),
    );

    render(<AiMessagePage />);
    await screen.findByText('메시지 1');

    await userEvent.click(screen.getByRole('button', { name: '공지' }));

    await waitFor(() =>
      expect(aiMessageApi.getMessages).toHaveBeenLastCalledWith(0, 20, 'NOTICE'),
    );
  });

  it('페이지가 하나뿐이면 페이지네이션을 숨긴다', async () => {
    aiMessageApi.getMessages.mockResolvedValue(pageResponse([message(1)]));

    render(<AiMessagePage />);
    await screen.findByText('메시지 1');

    expect(screen.queryByRole('button', { name: '다음' })).not.toBeInTheDocument();
  });

  it('첫 페이지에서는 이전 버튼이 비활성화되고 다음 페이지를 조회할 수 있다', async () => {
    aiMessageApi.getMessages.mockResolvedValue(
      pageResponse([message(1)], { number: 0, totalPages: 3 }),
    );

    render(<AiMessagePage />);
    await screen.findByText('메시지 1');

    expect(screen.getByRole('button', { name: '이전' })).toBeDisabled();
    expect(screen.getByText('1 / 3')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '다음' }));

    await waitFor(() =>
      expect(aiMessageApi.getMessages).toHaveBeenLastCalledWith(1, 20, 'ALL'),
    );
  });

  it('마지막 페이지에서는 다음 버튼이 비활성화된다', async () => {
    aiMessageApi.getMessages.mockResolvedValue(
      pageResponse([message(1)], { number: 2, totalPages: 3 }),
    );

    render(<AiMessagePage />);
    await screen.findByText('메시지 1');

    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '이전' })).toBeEnabled();
  });

  it('목록 행을 누르면 해당 메시지로 초안 모달이 열린다', async () => {
    aiMessageApi.getMessages.mockResolvedValue(pageResponse([message(7)]));

    render(<AiMessagePage />);
    await userEvent.click(await screen.findByText('메시지 7'));

    expect(screen.getByText('초안 모달: 메시지 7')).toBeInTheDocument();
  });
});
