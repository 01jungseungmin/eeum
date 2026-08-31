import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import AiMessageDraftModal from './AiMessageDraftModal';
import { aiMessageApi } from '../../../api/owner/aiMessageApi';

vi.mock('../../../api/owner/aiMessageApi', () => ({
  aiMessageApi: {
    updateMessage: vi.fn(),
    sendMessage: vi.fn(),
    scheduleMessage: vi.fn(),
    cancelMessage: vi.fn(),
    publishNotice: vi.fn(),
    scheduleNotice: vi.fn(),
  },
}));

const ok = (data) => ({ data: { data } });

const baseMessage = {
  messageId: 10,
  type: 'CUSTOMER_CARE',
  channel: 'APP_PUSH',
  status: 'DRAFT',
  title: '오랜만이에요',
  content: '이번 주 신메뉴 나왔어요.',
  originalContent: '이번 주 신메뉴 나왔어요.',
};

const renderModal = (overrides = {}, handlers = {}) => {
  const onClose = handlers.onClose ?? vi.fn();
  const onUpdated = handlers.onUpdated ?? vi.fn();
  render(
    <AiMessageDraftModal
      message={{ ...baseMessage, ...overrides }}
      onClose={onClose}
      onUpdated={onUpdated}
    />,
  );
  return { onClose, onUpdated };
};

describe('AiMessageDraftModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.spyOn(window, 'alert').mockImplementation(() => {});
    vi.spyOn(window, 'confirm').mockImplementation(() => true);
    aiMessageApi.updateMessage.mockResolvedValue(ok({ status: 'REVIEWED' }));
    aiMessageApi.sendMessage.mockResolvedValue(ok({ status: 'SENT' }));
    aiMessageApi.scheduleMessage.mockResolvedValue(ok({ status: 'SCHEDULED' }));
    aiMessageApi.cancelMessage.mockResolvedValue(ok({ status: 'CANCELLED' }));
    aiMessageApi.publishNotice.mockResolvedValue(ok({ status: 'SENT' }));
    aiMessageApi.scheduleNotice.mockResolvedValue(ok({ status: 'SCHEDULED' }));
  });

  it('전달된 메시지의 제목/문구를 폼에 채운다', () => {
    renderModal();

    expect(screen.getByLabelText('제목')).toHaveValue('오랜만이에요');
    expect(screen.getByLabelText('문구')).toHaveValue('이번 주 신메뉴 나왔어요.');
  });

  it('DRAFT 상태에서는 취소 버튼을 숨기고 안내 문구를 노출한다', () => {
    renderModal({ status: 'DRAFT' });

    expect(screen.queryByRole('button', { name: /취소/ })).not.toBeInTheDocument();
    expect(
      screen.getByText(/초안 상태에서는 바로 보낼 수 없어요/),
    ).toBeInTheDocument();
  });

  it('발송 완료 상태에서는 편집·발송 버튼을 모두 숨긴다', () => {
    renderModal({ status: 'SENT' });

    expect(screen.queryByRole('button', { name: /저장/ })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /검토 후 보내기/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText('제목')).toBeDisabled();
  });

  // DRAFT 는 백엔드에서 직접 발송이 막혀 있어 PATCH 선행이 필수다.
  it('DRAFT 를 보내면 수정(PATCH) 후 발송한다', async () => {
    const { onUpdated, onClose } = renderModal({ status: 'DRAFT' });

    await userEvent.click(screen.getByRole('button', { name: /검토 후 보내기/ }));

    await waitFor(() => expect(aiMessageApi.sendMessage).toHaveBeenCalled());
    expect(aiMessageApi.updateMessage).toHaveBeenCalledWith(10, {
      title: '오랜만이에요',
      content: '이번 주 신메뉴 나왔어요.',
    });
    expect(onUpdated).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('REVIEWED 를 수정 없이 보내면 PATCH 없이 바로 발송한다', async () => {
    renderModal({ status: 'REVIEWED' });

    await userEvent.click(screen.getByRole('button', { name: /검토 후 보내기/ }));

    await waitFor(() => expect(aiMessageApi.sendMessage).toHaveBeenCalledWith(10));
    expect(aiMessageApi.updateMessage).not.toHaveBeenCalled();
  });

  it('REVIEWED 라도 문구를 고쳤으면 수정 후 발송한다', async () => {
    renderModal({ status: 'REVIEWED' });

    await userEvent.type(screen.getByLabelText('문구'), ' 놀러오세요!');
    await userEvent.click(screen.getByRole('button', { name: /검토 후 보내기/ }));

    await waitFor(() => expect(aiMessageApi.updateMessage).toHaveBeenCalled());
    expect(aiMessageApi.updateMessage).toHaveBeenCalledWith(10, {
      title: '오랜만이에요',
      content: '이번 주 신메뉴 나왔어요. 놀러오세요!',
    });
    expect(aiMessageApi.sendMessage).toHaveBeenCalledWith(10);
  });

  it('NOTICE 유형은 발송 대신 공지 등록 API 를 호출한다', async () => {
    renderModal({ type: 'NOTICE', status: 'REVIEWED' });

    await userEvent.click(screen.getByRole('button', { name: /공지 등록하기/ }));

    await waitFor(() => expect(aiMessageApi.publishNotice).toHaveBeenCalledWith(10));
    expect(aiMessageApi.sendMessage).not.toHaveBeenCalled();
  });

  it('예약 발송은 첫 클릭에 입력을 열고, 시각 없이 확정하면 막는다', async () => {
    renderModal({ status: 'REVIEWED' });

    await userEvent.click(screen.getByRole('button', { name: /예약 발송/ }));
    expect(screen.getByLabelText('예약 발송 시각')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /예약 확정/ }));
    expect(window.alert).toHaveBeenCalledWith('예약 발송 시각을 선택해주세요.');
    expect(aiMessageApi.scheduleMessage).not.toHaveBeenCalled();
  });

  // datetime-local 값에는 초가 없어 LocalDateTime 파싱이 깨진다.
  it('예약 시각에 초를 붙여 전송한다', async () => {
    renderModal({ status: 'REVIEWED' });

    await userEvent.click(screen.getByRole('button', { name: /예약 발송/ }));
    await userEvent.type(
      screen.getByLabelText('예약 발송 시각'),
      '2026-09-01T10:30',
    );
    await userEvent.click(screen.getByRole('button', { name: /예약 확정/ }));

    await waitFor(() =>
      expect(aiMessageApi.scheduleMessage).toHaveBeenCalledWith(
        10,
        '2026-09-01T10:30:00',
      ),
    );
  });

  it('취소는 확인 창에서 거절하면 API 를 호출하지 않는다', async () => {
    window.confirm.mockReturnValue(false);
    renderModal({ status: 'REVIEWED' });

    await userEvent.click(screen.getByRole('button', { name: /^취소$/ }));

    expect(window.confirm).toHaveBeenCalled();
    expect(aiMessageApi.cancelMessage).not.toHaveBeenCalled();
  });

  it('실패 시 백엔드 문구를 노출하고 모달을 닫지 않는다', async () => {
    aiMessageApi.sendMessage.mockRejectedValue({
      response: { data: { error: { message: '이미 발송된 메시지예요.' } } },
    });
    const { onClose } = renderModal({ status: 'REVIEWED' });

    await userEvent.click(screen.getByRole('button', { name: /검토 후 보내기/ }));

    await waitFor(() =>
      expect(window.alert).toHaveBeenCalledWith('이미 발송된 메시지예요.'),
    );
    expect(onClose).not.toHaveBeenCalled();
  });

  it('문구를 고치면 AI 원본으로 되돌릴 수 있다', async () => {
    renderModal({ status: 'REVIEWED' });
    const content = screen.getByLabelText('문구');

    await userEvent.clear(content);
    await userEvent.type(content, '직접 쓴 문구');
    await userEvent.click(
      screen.getByRole('button', { name: /AI 원본 문구로 되돌리기/ }),
    );

    expect(content).toHaveValue('이번 주 신메뉴 나왔어요.');
  });
});
