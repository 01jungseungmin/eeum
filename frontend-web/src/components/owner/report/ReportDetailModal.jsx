import React from 'react';
import styled from 'styled-components';
import { X } from 'lucide-react';

const Overlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ModalBox = styled.div`
  background-color: #ffffff;
  width: 440px;
  max-width: 90%;
  border-radius: 20px;
  padding: 28px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
  position: relative;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;

  h2 {
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
    margin: 0;
  }
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  cursor: pointer;
  color: #94a3b8;
  padding: 4px;
  display: flex;
  align-items: center;

  &:hover {
    color: #475569;
  }
`;

const InfoRow = styled.div`
  display: flex;
  margin-bottom: 16px;
  font-size: 14px;
`;

const Label = styled.span`
  width: 100px;
  color: #94a3b8;
  font-weight: 500;
`;

const Value = styled.span`
  color: #0f172a;
  font-weight: 700;
`;

const SectionLabel = styled.div`
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
  margin-top: 20px;
  margin-bottom: 8px;
`;

const ContentBox = styled.div`
  background-color: #f8fafc;
  border-radius: 12px;
  padding: 14px 16px;
  font-size: 13px;
  color: #334155;
  line-height: 1.5;
`;

const ActionButton = styled.button`
  width: 100%;
  padding: 12px;
  margin-top: 24px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background-color: #ffffff;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    background-color: #f8fafc;
  }
`;

// 백엔드 Enum 매핑
const TARGET_TYPE_MAP = {
  STORE: '상점 신고',
  STORE_REVIEW: '리뷰 신고',
  COMMUNITY_POST: '게시글 신고',
  COMMUNITY_COMMENT: '댓글 신고',
  ACCOUNT: '계정 신고',
};

const REASON_MAP = {
  SPAM: '스팸/광고',
  ABUSE: '욕설/비방',
  FRAUD: '사기/기만',
  INAPPROPRIATE_CONTENT: '부적절한 콘텐츠',
  FALSE_INFORMATION: '허위 리뷰',
  PERSONAL_INFORMATION: '개인정보 침해',
  ETC: '기타',
};

export default function ReportDetailModal({ data, onClose, loading }) {
  if (!data && !loading) return null;

  const formattedId = data
    ? `RPT-${String(data.reportId).padStart(3, '0')}`
    : '';
  const formattedDate = data?.createdAt ? data.createdAt.split('T')[0] : '';

  return (
    <Overlay onClick={onClose}>
      <ModalBox onClick={(e) => e.stopPropagation()}>
        <Header>
          <h2>신고 상세</h2>
          <CloseButton onClick={onClose}>
            <X size={20} />
          </CloseButton>
        </Header>

        {loading ? (
          <div
            style={{ textAlign: 'center', padding: '40px 0', color: '#94a3b8' }}
          >
            상세 정보를 불러오는 중입니다...
          </div>
        ) : (
          <>
            <InfoRow>
              <Label>신고 번호</Label>
              <Value>{formattedId}</Value>
            </InfoRow>
            <InfoRow>
              <Label>신고 유형</Label>
              <Value>
                {TARGET_TYPE_MAP[data.targetType] || data.targetType}
              </Value>
            </InfoRow>
            <InfoRow>
              <Label>신고 분류</Label>
              <Value>{REASON_MAP[data.reason] || data.reason}</Value>
            </InfoRow>
            <InfoRow>
              <Label>신고자</Label>
              <Value>{data.reporterName}</Value>
            </InfoRow>
            <InfoRow>
              <Label>신고일</Label>
              <Value>{formattedDate}</Value>
            </InfoRow>

            <SectionLabel>신고 대상 내용</SectionLabel>
            <ContentBox>대상 ID: {data.targetId}</ContentBox>

            <SectionLabel>신고 사유</SectionLabel>
            <ContentBox>
              {data.content || '신고 상세 사유가 없습니다.'}
            </ContentBox>

            <ActionButton onClick={onClose}>닫기</ActionButton>
          </>
        )}
      </ModalBox>
    </Overlay>
  );
}
