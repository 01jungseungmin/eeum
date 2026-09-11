import React from 'react';
import {
  Tag,
  Megaphone,
  Star,
  Heart,
  MessageCircle,
  TrendingUp,
  ShieldCheck,
  Edit3,
} from 'lucide-react';

// 추천 질문 목록
export const QUICK_QUESTIONS = [
  {
    id: 1,
    icon: (
      <Tag
        size={14}
        color="#d97706"
      />
    ),
    text: '이번 주 이벤트 뭐 할까요?',
  },
  {
    id: 2,
    icon: (
      <Megaphone
        size={14}
        color="#2563eb"
      />
    ),
    text: '오늘 공지 문구 써줘',
  },
  {
    id: 3,
    icon: (
      <Star
        size={14}
        color="#ca8a04"
      />
    ),
    text: '우리 가게 리뷰 요약해줘',
  },
  {
    id: 4,
    icon: (
      <Heart
        size={14}
        color="#e11d48"
      />
    ),
    text: '단골 고객 메시지 써줘',
  },
  {
    id: 5,
    icon: (
      <MessageCircle
        size={14}
        color="#16a34a"
      />
    ),
    text: '미답변 문의 답변 초안 만들어줘',
  },
  {
    id: 6,
    icon: (
      <TrendingUp
        size={14}
        color="#0284c7"
      />
    ),
    text: '이번 이벤트 성과 요약해줘',
  },
  {
    id: 7,
    icon: (
      <ShieldCheck
        size={14}
        color="#16a34a"
      />
    ),
    text: '에너지·안전 점검 항목 알려줘',
  },
  {
    id: 8,
    icon: (
      <Edit3
        size={14}
        color="#4b5563"
      />
    ),
    text: '답글 초안 써줘',
  },
];

// 초기 AI 인사 메시지
export const INITIAL_CHAT_MESSAGES = [
  {
    id: 1,
    sender: 'ai',
    text: '안녕하세요 사장님!\n리뷰 답글, 공지 문구, 이벤트 추천, 고객 메시지, 문의 답변을 도와드릴 수 있어요.\n아래 질문 버튼을 선택해 주세요 😊',
    actions: [],
    outOfScope: false,
  },
];

// 액션 타입 매핑
export const ACTION_ROUTES = {
  OPEN_EVENT_REGISTER: '/events/register',
  OPEN_NOTICE_REGISTER: '/notice/register',
  OPEN_REVIEW_MANAGEMENT: '/reviews',
};
