import React from 'react';
import styled, { css } from 'styled-components';
import { Send, Check, Sparkles } from 'lucide-react';

/* variant에 따른 카드 배경색 분기 */
const CardContainer = styled.div`
  border-radius: 16px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: all 0.2s ease;

  ${(props) =>
    props.$variant === 'grid'
      ? css`
          background-color: #f9fafb; /* 메인 화면용 연한 회색 배경 */
          border: 1px solid #f3f4f6;
        `
      : css`
          background-color: #ffffff; /* 상세 페이지용 흰색 배경 */
          border: 1px solid #f3f4f6;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
        `}
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
`;

const HeaderTitleGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;

  .priority {
    font-size: 12px;
    color: #9ca3af;
    font-weight: 500;
  }

  h2 {
    font-size: 16px;
    font-weight: 700;
    color: #111827;
    margin: 0;
  }
`;

const CardCategoryIcon = styled.div`
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: ${(props) => props.$bgColor || '#ffffff'};
  color: ${(props) => props.$color || '#374151'};
`;

const TagChip = styled.span`
  background-color: #f0fdf4;
  color: #16a34a;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 12px;
`;

const PriorityBadge = styled.span`
  font-size: 11px;
  font-weight: 600;
  color: #15803d;
  background-color: #dcfce7;
  padding: 2px 8px;
  border-radius: 6px;
`;

const CardBodyRow = styled.div`
  display: flex;
  gap: 16px;
  align-items: flex-start;
  ${(props) =>
    props.$variant === 'grid' &&
    css`
      flex-direction: column;
      gap: 0;
    `}
`;

const ItemIconBox = styled.div`
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background-color: ${(props) => props.$bg || '#f3f4f6'};
  color: ${(props) => props.$color || '#4b5563'};
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
`;

const CardContent = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  width: 100%;

  .card-title {
    font-size: 15px;
    font-weight: 700;
    color: #111827;
    margin: 0 0 6px 0;
  }

  .description {
    font-size: 12px;
    color: #6b7280;
    margin: 0 0 16px 0;
    line-height: 1.45;
    ${(props) => props.$variant === 'grid' && 'height: 36px;'}
  }
`;

/* 메인 그리드(grid)일 때는 흰색 배경, 상세(detail)일 때는 연초록 배경으로 처리 */
const AiMessageBox = styled.div`
  border: 1px dashed #86efac;
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background-color: ${(props) =>
    props.$variant === 'grid' ? '#ffffff' : '#f0fdf4'};

  .ai-label {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    font-weight: 700;
    color: #16a34a;
  }

  .message-text {
    font-size: 12px;
    color: #374151;
    margin: 0;
    line-height: 1.5;
  }
`;

const ActionButton = styled.button`
  width: 100%;
  padding: 10px;
  background-color: ${(props) =>
    props.$isSent ? '#f3f4f6' : props.$secondary ? '#ffffff' : '#3bba84'};
  color: ${(props) =>
    props.$isSent ? '#9ca3af' : props.$secondary ? '#374151' : '#ffffff'};
  border: ${(props) =>
    props.$isSent ? 'none' : props.$secondary ? '1px solid #e5e7eb' : 'none'};
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: ${(props) => (props.$isSent ? 'default' : 'pointer')};
  transition: opacity 0.2s;

  &:hover {
    opacity: ${(props) => (props.$isSent ? 1 : 0.9)};
  }
`;

export default function CustomerCareCard({
  data,
  isSent = false,
  variant = 'grid', // 'grid' | 'detail'
  onActionClick,
}) {
  const {
    icon: Icon,
    iconBgColor,
    iconColor,
    priority,
    title,
    description,
    message,
    tag,
    secondaryAction,
  } = data;

  return (
    <CardContainer $variant={variant}>
      <div>
        {/* 헤더 영역 */}
        <CardHeader>
          {variant === 'detail' ? (
            <HeaderTitleGroup>
              <span className="priority">{priority}</span>
              <h2>{title}</h2>
            </HeaderTitleGroup>
          ) : (
            <CardCategoryIcon
              $bgColor={iconBgColor}
              $color={iconColor}
            >
              {Icon && <Icon size={16} />}
            </CardCategoryIcon>
          )}

          {variant === 'detail' && tag ? (
            <TagChip>{tag}</TagChip>
          ) : (
            <PriorityBadge>{priority}</PriorityBadge>
          )}
        </CardHeader>

        {/* 바디 영역 */}
        <CardBodyRow $variant={variant}>
          {variant === 'detail' && (
            <ItemIconBox
              $bg={iconBgColor}
              $color={iconColor}
            >
              {Icon && <Icon size={22} />}
            </ItemIconBox>
          )}

          <CardContent $variant={variant}>
            {variant === 'grid' && <h4 className="card-title">{title}</h4>}
            <p className="description">{description}</p>

            {message && (
              <AiMessageBox $variant={variant}>
                <div className="ai-label">
                  <Sparkles size={12} /> AI 준비 메시지
                </div>
                <p className="message-text">{message}</p>
              </AiMessageBox>
            )}
          </CardContent>
        </CardBodyRow>
      </div>

      {/* 액션 버튼 (메인 grid 모드에서만 하단 노출) */}
      {variant === 'grid' && (
        <ActionButton
          $isSent={isSent}
          $secondary={secondaryAction}
          onClick={() => onActionClick && onActionClick(data)}
        >
          {isSent ? (
            <>
              <Check size={14} /> 전송 예약됨
            </>
          ) : secondaryAction ? (
            '답변 초안 보기'
          ) : (
            <>
              <Send size={14} /> 검토 후 보내기
            </>
          )}
        </ActionButton>
      )}
    </CardContainer>
  );
}
