import { describe, it, expect } from 'vitest';

import {
  AI_CARE_TYPE_MAP,
  AI_CHANNEL_MAP,
  AI_EDITABLE_STATUSES,
  AI_MESSAGE_STATUS_COLOR,
  AI_MESSAGE_STATUS_MAP,
  AI_MESSAGE_TYPE_MAP,
  AI_MESSAGE_TYPE_TABS,
  AI_RISK_LEVEL_COLOR,
  AI_RISK_LEVEL_MAP,
  AI_TONE_MAP,
  AI_TONE_OPTIONS,
  AI_TRANSITABLE_STATUSES,
} from './aiConstants';

// 백엔드 enum 과 라벨 맵의 키가 어긋나면 화면에 원문 enum 이 그대로 노출되므로
// 키 집합 자체를 고정해둔다. 백엔드에 값이 추가되면 이 테스트가 먼저 깨진다.
describe('aiConstants 라벨 맵', () => {
  it('AiCareType 3종을 모두 매핑한다', () => {
    expect(Object.keys(AI_CARE_TYPE_MAP)).toEqual([
      'CART_INTEREST',
      'INACTIVE_REGULAR',
      'INQUIRY_HESITATION',
    ]);
  });

  it('AiMessageStatus 6종을 모두 매핑한다', () => {
    expect(Object.keys(AI_MESSAGE_STATUS_MAP)).toEqual([
      'DRAFT',
      'REVIEWED',
      'SENT',
      'SCHEDULED',
      'FAILED',
      'CANCELLED',
    ]);
  });

  it('AiMessageType 9종을 모두 매핑한다', () => {
    expect(Object.keys(AI_MESSAGE_TYPE_MAP)).toHaveLength(9);
    expect(AI_MESSAGE_TYPE_MAP.CUSTOMER_CARE).toBe('고객 케어');
    expect(AI_MESSAGE_TYPE_MAP.NOTICE).toBe('공지');
  });

  it('AiChannel / AiTone / AiRiskLevel 을 모두 매핑한다', () => {
    expect(Object.keys(AI_CHANNEL_MAP)).toEqual([
      'APP_PUSH',
      'KAKAO_ALERT',
      'STORE_NOTICE',
      'SNS_CARD',
    ]);
    expect(Object.keys(AI_TONE_MAP)).toEqual(['POLITE', 'FRIENDLY', 'SHORT']);
    expect(Object.keys(AI_RISK_LEVEL_MAP)).toEqual([
      'NORMAL',
      'CAUTION',
      'WARNING',
    ]);
  });

  it('배지 색상 맵은 라벨 맵과 동일한 키를 가진다', () => {
    expect(Object.keys(AI_MESSAGE_STATUS_COLOR)).toEqual(
      Object.keys(AI_MESSAGE_STATUS_MAP),
    );
    expect(Object.keys(AI_RISK_LEVEL_COLOR)).toEqual(
      Object.keys(AI_RISK_LEVEL_MAP),
    );
  });
});

describe('상태 전이 규칙', () => {
  it('DRAFT 는 수정 가능하지만 발송·취소는 불가하다', () => {
    expect(AI_EDITABLE_STATUSES).toContain('DRAFT');
    expect(AI_TRANSITABLE_STATUSES).not.toContain('DRAFT');
  });

  it('발송·취소 가능 상태는 수정 가능 상태의 부분집합이다', () => {
    AI_TRANSITABLE_STATUSES.forEach((status) => {
      expect(AI_EDITABLE_STATUSES).toContain(status);
    });
  });

  it('종료 상태(SENT/FAILED/CANCELLED)는 수정도 전이도 불가하다', () => {
    ['SENT', 'FAILED', 'CANCELLED'].forEach((status) => {
      expect(AI_EDITABLE_STATUSES).not.toContain(status);
      expect(AI_TRANSITABLE_STATUSES).not.toContain(status);
    });
  });
});

describe('UI 옵션 파생 값', () => {
  it('메시지 유형 탭은 전체 + 유형 전체를 순서대로 담는다', () => {
    expect(AI_MESSAGE_TYPE_TABS[0]).toEqual({ label: '전체', value: 'ALL' });
    expect(AI_MESSAGE_TYPE_TABS).toHaveLength(
      Object.keys(AI_MESSAGE_TYPE_MAP).length + 1,
    );
  });

  it('톤 옵션은 label/value 쌍으로 변환된다', () => {
    expect(AI_TONE_OPTIONS).toContainEqual({
      value: 'FRIENDLY',
      label: '친근한 톤',
    });
  });
});
