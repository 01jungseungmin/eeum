import React, { useState } from 'react';
import styled from 'styled-components';
import { MessageSquare, Flag, Star } from 'lucide-react';

// --- Styled Components (경고 방지를 위해 transient props `$` 적용) ---
const Card = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #f3f4f6;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  margin-bottom: 16px;
`;

const ReviewHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
`;

const UserInfo = styled.div`
  display: flex;
  gap: 12px;
  align-items: center;
`;

const Avatar = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #eef2ff;
  color: #4f46e5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
`;

const AuthorName = styled.div`
  font-weight: 700;
  font-size: 14px;
  color: #111827;
  margin-bottom: 4px;
`;

const RatingRow = styled.div`
  display: flex;
  align-items: center;
  gap: 2px;
  color: #fbbf24;
`;

const DateText = styled.span`
  color: #9ca3af;
  font-size: 13px;
  margin-left: 8px;
`;

const RightBadgeBlock = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const ProductTag = styled.span`
  background: #eef2ff;
  color: #4f46e5;
  padding: 6px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
`;

const UnansweredBadge = styled.span`
  background: #fee2e2;
  color: #ef4444;
  padding: 4px 10px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
`;

const Content = styled.p`
  font-size: 14px;
  color: #1f2937;
  margin: 16px 0;
  line-height: 1.6;
`;

const ActionButtonGroup = styled.div`
  display: flex;
  gap: 10px;
  margin-top: 14px;
`;

const ReplyTriggerButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  background-color: #f0fdf4;
  border: 1px solid #dcfce7;
  color: #166534;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  &:hover {
    background-color: #e8f5e9;
  }
`;

const ReportTriggerButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  background-color: #ffffff;
  border: 1px solid #f3f4f6;
  color: #b45309;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
  &:hover {
    background-color: #fffbeb;
    border-color: #fde68a;
  }
`;

const ReplyFormContainer = styled.div`
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 16px;
  background: #ffffff;
  margin-top: 12px;
`;

const TextArea = styled.textarea`
  width: 100%;
  border: none;
  resize: none;
  height: 80px;
  outline: none;
  font-size: 14px;
  color: #374151;
`;

const FormActions = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  border-top: 1px solid #f3f4f6;
  padding-top: 12px;
`;

const CharCount = styled.span`
  color: #9ca3af;
  font-size: 13px;
`;

const CancelButton = styled.button`
  padding: 8px 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  margin-right: 8px;
`;

const SubmitButton = styled.button`
  padding: 8px 16px;
  background: ${(props) => (props.$isEdit ? '#3b82f6' : '#10b981')};
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  &:hover {
    background: ${(props) => (props.$isEdit ? '#2563eb' : '#059669')};
  }
`;

const AdminActionButtonGroup = styled.div`
  display: none;
  gap: 12px;
`;

const ReplyDisplayContainer = styled.div`
  background: #f0fdf4;
  border: 1px solid #dcfce7;
  border-radius: 12px;
  padding: 16px;
  margin-top: 16px;
  position: relative;

  &:hover ${AdminActionButtonGroup} {
    display: flex;
  }
`;

const ReplyInsideHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
`;

const ReplyAuthorInfo = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
 wedge;
`;

const OwnerAvatar = styled.div`
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #10b981;
  color: white;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
`;

const OwnerName = styled.span`
  font-weight: 700;
  font-size: 13px;
  color: #166534;
`;

const ReplyContent = styled.p`
  font-size: 13px;
  color: #1f2937;
  margin: 0;
  line-height: 1.5;
`;

const AdminButton = styled.button`
  background: none;
  border: none;
  color: #9ca3af;
  font-size: 12px;
  cursor: pointer;
  &:hover {
    color: #4b5563;
    text-decoration: underline;
  }
`;

function formatDate(isoString) {
  if (!isoString) return '';
  return isoString.split('T')[0];
}

export default function ReviewItem({
  review,
  onCreateReply,
  onUpdateReply,
  onDeleteReply,
  onOpenReport,
}) {
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [replyInput, setReplyInput] = useState('');

  const productLabel =
    review.orderItems && review.orderItems.length > 0
      ? review.orderItems.length > 1
        ? `${review.orderItems[0].productName} 외 ${review.orderItems.length - 1}건`
        : review.orderItems[0].productName
      : '구매 상품 정보 없음';

  const handleOpenForm = () => {
    setReplyInput(review.reply ? review.reply.content : '');
    setIsFormOpen(true);
  };

  const handleSubmit = async (e) => {
    if (e) e.preventDefault(); // 이벤트 버블링 방지

    const sendContent = replyInput.trim(); // 상태값을 직접 가공하여 로컬 변수에 확보
    if (!sendContent) {
      alert('답글 내용을 입력해주세요.');
      return;
    }

    if (review.reply) {
      // 컴포넌트 내부 State가 아닌 확보된 텍스트 변수(sendContent)를 직계 전달
      await onUpdateReply(review.storereviewId, sendContent);
    } else {
      await onCreateReply(review.storereviewId, sendContent);
    }
    setIsFormOpen(false);
  };

  return (
    <Card>
      <ReviewHeader>
        <UserInfo>
          <Avatar>{review.nickname ? review.nickname[0] : '익'}</Avatar>
          <div>
            <AuthorName>{review.nickname || '익명 고객'}</AuthorName>
            <RatingRow>
              {[...Array(5)].map((_, i) => (
                <Star
                  key={i}
                  size={14}
                  fill={i < review.rating ? '#fbbf24' : '#e5e7eb'}
                  stroke="none"
                />
              ))}
              <DateText>{formatDate(review.createdAt)}</DateText>
            </RatingRow>
          </div>
        </UserInfo>
        <RightBadgeBlock>
          <ProductTag>{productLabel}</ProductTag>
          {!review.reply && <UnansweredBadge>미답글</UnansweredBadge>}
        </RightBadgeBlock>
      </ReviewHeader>

      <Content>{review.content}</Content>

      {isFormOpen ? (
        <ReplyFormContainer>
          <TextArea
            placeholder="고객에게 감사의 마음을 전해보세요 (최대 150자)"
            maxLength={150}
            value={replyInput}
            onChange={(e) => setReplyInput(e.target.value)}
          />
          <FormActions>
            <CharCount>{replyInput.length}/150자</CharCount>
            <div>
              <CancelButton onClick={() => setIsFormOpen(false)}>
                취소
              </CancelButton>
              <SubmitButton $isEdit={!!review.reply} onClick={handleSubmit}>
                {review.reply ? '수정 완료' : '답글 등록'}
              </SubmitButton>
            </div>
          </FormActions>
        </ReplyFormContainer>
      ) : review.reply ? (
        <ReplyDisplayContainer>
          <ReplyInsideHeader>
            <ReplyAuthorInfo>
              <OwnerAvatar>사</OwnerAvatar>
              <OwnerName>{review.reply.nickname || '사장님'} 답글</OwnerName>
              <DateText>
                {formatDate(review.reply.modifiedAt || review.reply.createdAt)}
              </DateText>
            </ReplyAuthorInfo>
            <AdminActionButtonGroup>
              <AdminButton onClick={handleOpenForm}>수정</AdminButton>
              <AdminButton onClick={() => onDeleteReply(review.storereviewId)}>
                삭제
              </AdminButton>
            </AdminActionButtonGroup>
          </ReplyInsideHeader>
          <ReplyContent>{review.reply.content}</ReplyContent>
        </ReplyDisplayContainer>
      ) : (
        <ActionButtonGroup>
          <ReplyTriggerButton onClick={handleOpenForm}>
            <MessageSquare size={14} /> 답글 달기
          </ReplyTriggerButton>
          <ReportTriggerButton
            onClick={() => onOpenReport(review.storereviewId)}
          >
            <Flag size={14} /> 악성 리뷰 신고
          </ReportTriggerButton>
        </ActionButtonGroup>
      )}

      {review.reply && !isFormOpen && (
        <div
          style={{
            marginTop: '12px',
            display: 'flex',
            justifyContent: 'flex-start',
          }}
        >
          <ReportTriggerButton
            onClick={() => onOpenReport(review.storereviewId)}
          >
            <Flag size={14} /> 악성 리뷰 신고
          </ReportTriggerButton>
        </div>
      )}
    </Card>
  );
}
