import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { Clock, Check, ChevronDown, ChevronUp } from 'lucide-react';
import { inquiryApi } from '../../../api/owner/inquiryApi';

const ItemCard = styled.div`
  background: #ffffff;
  border: 1px solid #e9ecef;
  border-radius: 16px;
  margin-bottom: 14px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.01);
`;

const CardHeaderTrigger = styled.div`
  padding: 22px 24px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  &:hover {
    background: #fafafa;
  }
`;

const HeaderTop = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
`;

const BadgeGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const StatusIconCircle = styled.div`
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: ${(props) => (props.$isPending ? '#fff0f0' : '#e6f4ea')};
  color: ${(props) => (props.$isPending ? '#fa5252' : '#40c057')};
`;

const StatusTextBadge = styled.span`
  background: #fff0f0;
  color: #fa5252;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
`;

const CategoryBadge = styled.span`
  background: #e6f4ea;
  color: #2b8a3e;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
`;

const CreatedDate = styled.span`
  font-size: 13px;
  color: #adb5bd;
`;

const HeaderMain = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
`;

const TitleBlock = styled.div`
  display: flex;
  flex-direction: column;
  flex: 1;
`;

const Title = styled.h3`
  font-size: 16px;
  font-weight: 700;
  color: #212529;
  margin: 0 0 8px 0;
`;

const WriterRow = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
`;

const Avatar = styled.div`
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #40c057;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
`;

const WriterName = styled.span`
  font-weight: 600;
  color: #495057;
  white-space: nowrap;
`;

const InlinePreviewText = styled.span`
  color: #868e96;
  font-weight: 400;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 550px;
`;

const ToggleIconWrapper = styled.div`
  color: #adb5bd;
`;

const ExpandedContent = styled.div`
  padding: 0 24px 24px 24px;
  border-top: 1px solid #f8f9fa;
`;

const SectionTitle = styled.h4`
  font-size: 13px;
  font-weight: 600;
  color: #868e96;
  margin: 18px 0 8px 0;
`;

const ContentBox = styled.div`
  background: #f8f9fa;
  border-radius: 10px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.6;
  color: #343a40;
`;

const ActionButton = styled.button`
  background: #40c057;
  color: #ffffff;
  border: none;
  padding: 10px 20px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  margin-top: 16px;
  transition: background 0.2s;
  &:hover {
    background: #37b24d;
  }
`;

const AnswerFormContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
`;

const TextArea = styled.textarea`
  width: 100%;
  height: 110px;
  padding: 14px;
  border: 1px solid #dee2e6;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
  resize: none;
  outline: none;
  &:focus {
    border-color: #40c057;
  }
`;

const FormFooter = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const CharCounter = styled.span`
  font-size: 13px;
  color: #adb5bd;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 8px;
`;

const CancelButton = styled.button`
  background: #ffffff;
  color: #495057;
  border: 1px solid #dee2e6;
  padding: 9px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
`;

const SubmitButton = styled.button`
  background: #40c057;
  color: #ffffff;
  border: none;
  padding: 9px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  &:disabled {
    background: #c3fae8;
    color: #94d82d;
    cursor: not-allowed;
  }
`;

const OwnerAnswerBox = styled.div`
  background: #f4fdf7;
  border: 1px solid #c3e6cb;
  border-radius: 12px;
  padding: 16px;
  margin-top: 8px;
`;

const OwnerHeader = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
`;

const OwnerAvatar = styled.div`
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #40c057;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
`;

const OwnerName = styled.span`
  font-size: 13px;
  font-weight: 700;
  color: #2b2b2b;
`;

const OwnerDate = styled.span`
  font-size: 13px;
  color: #adb5bd;
`;

const AnswerText = styled.div`
  font-size: 14px;
  color: #333333;
  line-height: 1.6;
`;

const CATEGORY_TEXT_MAP = {
  STORE: '상품 문의',
  ORDER: '주문 문의',
  RESERVATION: '예약 문의',
  PAYMENT: '결제 문의',
  ETC: '기타',
};

export default function InquiryItem({ item: initialItem, onRefresh }) {
  const [isExpanded, setIsExpanded] = useState(false);
  const [item, setItem] = useState(initialItem);
  const [isReplying, setIsReplying] = useState(false);
  const [answerContent, setAnswerContent] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const isPending = item.status === 'PENDING';
  const categoryText = CATEGORY_TEXT_MAP[item.category] || '상품 문의';

  useEffect(() => {
    // 문의 상세 정보 조회
    const prefetchDetail = async () => {
      try {
        const res = await inquiryApi.getInquiryDetail(initialItem.inquiryId);
        if (res.data?.success && res.data?.data) {
          setItem(res.data.data);
        }
      } catch (error) {
        console.error(
          `Inquiry ${initialItem.inquiryId} 사전 조회 실패:`,
          error,
        );
      }
    };

    // 만약 처음에 전달받은 객체에 content가 비어있을 때만 사전 조회 작동
    if (!initialItem.content) {
      prefetchDetail();
    }
  }, [initialItem.inquiryId, initialItem.content]);

  // 클릭 시에는 이제 서버 요청을 기다릴 필요 없이 상태만 바로 슥 열어주면 됩니다!
  const handleToggleExpand = () => {
    setIsExpanded(!isExpanded);
  };

  const handleSubmitAnswer = async () => {
    if (!answerContent.trim()) return;

    setIsLoading(true);
    try {
      const res = await inquiryApi.createAnswer(item.inquiryId, answerContent);

      if (res.data?.success) {
        alert('답변이 성공적으로 등록되었습니다.');
        setIsReplying(false);
        setAnswerContent('');

        const refreshDetail = await inquiryApi.getInquiryDetail(item.inquiryId);
        if (refreshDetail.data?.success && refreshDetail.data?.data) {
          setItem(refreshDetail.data.data);
        }

        if (onRefresh) onRefresh();
      } else {
        alert(res.data?.message || '답변 등록에 실패했습니다.');
      }
    } catch (error) {
      alert('서버 통신 중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <ItemCard>
      <CardHeaderTrigger onClick={handleToggleExpand}>
        <HeaderTop>
          <BadgeGroup>
            <StatusIconCircle $isPending={isPending}>
              {isPending ? <Clock size={16} /> : <Check size={16} />}
            </StatusIconCircle>
            {isPending && <StatusTextBadge>미답변</StatusTextBadge>}
            <CategoryBadge>{categoryText}</CategoryBadge>
          </BadgeGroup>
          <CreatedDate>
            {item.createdAt ? item.createdAt.substring(0, 10) : ''}
          </CreatedDate>
        </HeaderTop>

        <HeaderMain>
          <TitleBlock>
            <Title>{item.title}</Title>
            <WriterRow>
              <Avatar>{item.writerName ? item.writerName[0] : '고'}</Avatar>
              <WriterName>{item.writerName || '고객'}</WriterName>
              {!isExpanded && item.content && (
                <InlinePreviewText>· {item.content}</InlinePreviewText>
              )}
            </WriterRow>
          </TitleBlock>
          <ToggleIconWrapper>
            {isExpanded ? <ChevronUp size={20} /> : <ChevronDown size={20} />}
          </ToggleIconWrapper>
        </HeaderMain>
      </CardHeaderTrigger>

      {isExpanded && (
        <ExpandedContent>
          <SectionTitle>고객 문의 내용</SectionTitle>
          <ContentBox>{item.content}</ContentBox>

          {isPending ? (
            !isReplying ? (
              <ActionButton onClick={() => setIsReplying(true)}>
                답변하기
              </ActionButton>
            ) : (
              <AnswerFormContainer>
                <SectionTitle>답변 작성</SectionTitle>
                <TextArea
                  placeholder="고객 문의에 성실하게 답변해 주세요..."
                  value={answerContent}
                  onChange={(e) => setAnswerContent(e.target.value)}
                  disabled={isLoading}
                />
                <FormFooter>
                  <CharCounter>{answerContent.length}자</CharCounter>
                  <ButtonGroup>
                    <CancelButton
                      onClick={() => {
                        setIsReplying(false);
                        setAnswerContent('');
                      }}
                      disabled={isLoading}
                    >
                      취소
                    </CancelButton>
                    <SubmitButton
                      onClick={handleSubmitAnswer}
                      disabled={isLoading || !answerContent.trim()}
                    >
                      {isLoading ? '등록 중...' : '답변 등록'}
                    </SubmitButton>
                  </ButtonGroup>
                </FormFooter>
              </AnswerFormContainer>
            )
          ) : (
            item.answers &&
            item.answers.length > 0 && (
              <>
                <SectionTitle>사장님 답변</SectionTitle>
                <OwnerAnswerBox>
                  <OwnerHeader>
                    <OwnerAvatar>사</OwnerAvatar>
                    <OwnerName>사장님 답변</OwnerName>
                    <OwnerDate>
                      {item.answers[0].createdAt?.substring(0, 10)}
                    </OwnerDate>
                  </OwnerHeader>
                  <AnswerText>{item.answers[0].content}</AnswerText>
                </OwnerAnswerBox>
              </>
            )
          )}
        </ExpandedContent>
      )}
    </ItemCard>
  );
}
