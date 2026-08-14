import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import styled from 'styled-components';
import { ArrowLeft, Send, Store, User, Lock } from 'lucide-react';
import { inquiryApi } from '../../../api/admin/inquiryApi';
import {
  INQUIRY_STATUS_INFO,
  INQUIRY_STATUS,
  INQUIRY_CATEGORY_MAP,
  TARGET_TYPE_MAP,
} from '../../../constants/inquiryConstants';

const Container = styled.div`
  padding: 32px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  background-color: #f9fafb;
  min-height: 100vh;
`;

const LoadingText = styled.div`
  padding: 80px 0;
  text-align: center;
  color: #6b7280;
  font-size: 16px;
`;

const HeaderSection = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;

const BackButton = styled.button`
  background: transparent;
  border: none;
  padding: 8px;
  border-radius: 8px;
  color: #4b5563;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    background-color: #f3f4f6;
  }
`;

const Title = styled.h1`
  font-size: 24px;
  font-weight: 700;
  color: #111827;
  margin: 0;
`;

const Subtitle = styled.p`
  font-size: 13px;
  color: #6b7280;
  margin-top: 2px;
`;

const ContentGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr;
  gap: 24px;

  @media (min-width: 1024px) {
    grid-template-columns: 2fr 1fr;
  }
`;

const MainColumn = styled.div`
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

const SideColumn = styled.div``;

const Card = styled.div`
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  padding: 24px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
`;

const CardHeader = styled.div`
  margin-bottom: 16px;
`;

const CardTitle = styled.h2`
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  margin: 0;

  &.sub {
    font-size: 16px;
  }
`;

const CardContent = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const BadgeGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
`;

const CategoryBadge = styled.span`
  background-color: #2563eb;
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 6px;
`;

const SecretBadge = styled.span`
  background-color: #f3f4f6;
  color: #4b5563;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 8px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
`;

const StatusBadge = styled.span`
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 6px;

  ${(props) =>
    props.$type === 'waiting' &&
    `
    background-color: #FEF3C7;
    color: #D97706;
  `}
  ${(props) =>
    props.$type === 'done' &&
    `
    background-color: #D1FAE5;
    color: #059669;
  `}
  ${(props) =>
    props.$type === 'processing' &&
    `
    background-color: #DBEAFE;
    color: #2563EB;
  `}
`;

const QuestionBox = styled.div`
  background-color: #f8fafc;
  border: 1px solid #f1f5f9;
  padding: 20px;
  border-radius: 12px;
  font-size: 15px;
  color: #1f2937;
  line-height: 1.6;
  white-space: pre-wrap;
`;

const MetaText = styled.div`
  font-size: 12px;
  color: #9ca3af;
  display: flex;
  gap: 16px;
`;

const AnswerList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const AnswerItem = styled.div`
  background-color: #f0fdf4;
  border: 1px solid #bbf7d0;
  padding: 16px;
  border-radius: 12px;
`;

const AnswerHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
`;

const AnswerWriter = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #111827;
`;

const WriterTypeBadge = styled.span`
  background-color: #059669;
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
`;

const AnswerDate = styled.span`
  font-size: 12px;
  color: #6b7280;
`;

const AnswerContent = styled.div`
  font-size: 14px;
  color: #1f2937;
  line-height: 1.5;
  white-space: pre-wrap;
`;

const WarningBox = styled.div`
  background-color: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;
`;

const StyledTextarea = styled.textarea`
  width: 100%;
  padding: 16px;
  background-color: #f3f4f6;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  color: #111827;
  resize: none;
  outline: none;
  box-sizing: border-box;

  &::placeholder {
    color: #9ca3af;
  }

  &:focus {
    box-shadow: 0 0 0 1px #059669;
  }
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 12px;
  margin-top: 8px;
`;

const CancelButton = styled.button`
  flex: 1;
  height: 44px;
  background-color: transparent;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #f9fafb;
  }
`;

const SubmitButton = styled.button`
  flex: 1;
  height: 44px;
  background-color: #059669;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #047857;
  }

  &:disabled {
    background-color: #9ca3af;
    cursor: not-allowed;
  }
`;

const InfoRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 14px;
  border-bottom: 1px solid #f3f4f6;

  &:last-child {
    border-bottom: none;
  }

  .label {
    color: #6b7280;
    display: flex;
    align-items: center;
  }

  .value {
    color: #111827;
    font-weight: 500;
  }
`;

export default function InquiryDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [inquiry, setInquiry] = useState(null);
  const [loading, setLoading] = useState(true);
  const [reply, setReply] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // 날짜 포맷팅 함수 (YYYY.MM.DD HH:mm)
  const formatDate = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(
      date.getDate(),
    ).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(
      date.getMinutes(),
    ).padStart(2, '0')}`;
  };

  // 상세 데이터 불러오기
  const fetchInquiryDetail = async () => {
    try {
      setLoading(true);
      const response = await inquiryApi.getInquiryDetail(id);
      if (response.data.success) {
        setInquiry(response.data.data);
      }
    } catch (error) {
      console.error('문의 상세 데이터를 불러오는 중 오류 발생:', error);
      alert('문의 정보를 불러올 수 없습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (id) {
      fetchInquiryDetail();
    }
  }, [id]);

  // 답변 등록 처리 (Request Body: { content: reply })
  const handleSendReply = async () => {
    if (!reply.trim()) {
      alert('답변 내용을 입력해주세요.');
      return;
    }

    try {
      setSubmitting(true);
      const response = await inquiryApi.createAnswer(id, reply);

      if (response.data.success) {
        const newAnswer = response.data.data;

        // 생성된 단일 답변을 목록에 반영 및 상태 업데이트
        setInquiry((prev) => ({
          ...prev,
          status: INQUIRY_STATUS.ANSWERED,
          answers: prev?.answers ? [...prev.answers, newAnswer] : [newAnswer],
        }));

        alert('답변이 성공적으로 등록되었습니다.');
        setReply('');
      }
    } catch (error) {
      console.error('답변 등록 실패:', error);
      alert('답변 등록 중 오류가 발생했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  // 💡 상수를 활용한 상태 뱃지 렌더링 함수
  const renderStatusBadge = (status) => {
    const info = INQUIRY_STATUS_INFO[status] || {
      label: status,
      type: 'waiting',
    };
    return <StatusBadge $type={info.type}>{info.label}</StatusBadge>;
  };

  if (loading) {
    return (
      <Container>
        <LoadingText>문의 상세 정보를 불러오는 중...</LoadingText>
      </Container>
    );
  }

  if (!inquiry) {
    return (
      <Container>
        <LoadingText>존재하지 않거나 삭제된 문의입니다.</LoadingText>
      </Container>
    );
  }

  // 💡 이미 답변이 등록되었는지 판단
  const isAnswered =
    inquiry.status === INQUIRY_STATUS.ANSWERED ||
    inquiry.status === INQUIRY_STATUS.COMPLETED ||
    (inquiry.answers && inquiry.answers.length > 0);

  return (
    <Container>
      {/* 헤더 영역 */}
      <HeaderSection>
        <BackButton onClick={() => navigate('/admin/inquiry')}>
          <ArrowLeft size={20} />
        </BackButton>
        <div>
          <Title>고객 문의 상세</Title>
          <Subtitle>문의 #{inquiry.inquiryId || id}</Subtitle>
        </div>
      </HeaderSection>

      <ContentGrid>
        {/* 좌측 메인 영역 */}
        <MainColumn>
          {/* 문의 질문 상세 카드 */}
          <Card>
            <CardHeader>
              <BadgeGroup>
                {/* 💡 ORDER -> 주문 문의로 한글 변환 */}
                <CategoryBadge>
                  {INQUIRY_CATEGORY_MAP[inquiry.category] ||
                    inquiry.category ||
                    '기타 문의'}
                </CategoryBadge>
                {renderStatusBadge(inquiry.status)}
                {inquiry.secret && (
                  <SecretBadge>
                    <Lock size={12} /> 비밀글
                  </SecretBadge>
                )}
              </BadgeGroup>
              <CardTitle>{inquiry.title}</CardTitle>
            </CardHeader>

            <CardContent>
              <QuestionBox>{inquiry.content}</QuestionBox>
              <MetaText>
                <div>작성일: {formatDate(inquiry.createdAt)}</div>
              </MetaText>
            </CardContent>
          </Card>

          {/* 등록된 답변 목록 */}
          {inquiry.answers && inquiry.answers.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle className="sub">
                  등록된 답변 ({inquiry.answers.length})
                </CardTitle>
              </CardHeader>
              <CardContent>
                <AnswerList>
                  {inquiry.answers.map((ans, idx) => (
                    <AnswerItem key={ans.answerId || idx}>
                      <AnswerHeader>
                        <AnswerWriter>
                          {/* 💡 ADMIN -> 관리자로 한글 변환 */}
                          <WriterTypeBadge>
                            {TARGET_TYPE_MAP[ans.writerType] ||
                              TARGET_TYPE_MAP[ans.writerRole] ||
                              ans.writerType ||
                              '관리자'}
                          </WriterTypeBadge>
                          <strong>{ans.writerName || '관리자'}</strong>
                        </AnswerWriter>
                        <AnswerDate>{formatDate(ans.createdAt)}</AnswerDate>
                      </AnswerHeader>
                      <AnswerContent>{ans.content}</AnswerContent>
                    </AnswerItem>
                  ))}
                </AnswerList>
              </CardContent>
            </Card>
          )}

          {/* 답변 작성 폼 (미답변 시에만 노출) */}
          {!isAnswered && (
            <Card>
              <CardHeader>
                <CardTitle className="sub">답변 작성</CardTitle>
              </CardHeader>
              <CardContent>
                <WarningBox>
                  ⚠️ 답변을 등록하면 상태가 [답변완료]로 변경되며 추가 등록이
                  불가능합니다.
                </WarningBox>
                <StyledTextarea
                  placeholder="답변 내용을 입력하세요..."
                  value={reply}
                  onChange={(e) => setReply(e.target.value)}
                  rows={6}
                  disabled={submitting}
                />
                <ButtonGroup>
                  <CancelButton onClick={() => navigate('/admin/inquiry')}>
                    목록으로
                  </CancelButton>
                  <SubmitButton
                    onClick={handleSendReply}
                    disabled={submitting}
                  >
                    <Send size={16} />
                    {submitting ? '등록 중...' : '답변 등록'}
                  </SubmitButton>
                </ButtonGroup>
              </CardContent>
            </Card>
          )}
        </MainColumn>

        {/* 우측 작성자/가게 정보 카드 */}
        <SideColumn>
          <Card>
            <CardHeader>
              <CardTitle className="sub">작성자 및 가게 정보</CardTitle>
            </CardHeader>
            <CardContent>
              <InfoRow>
                <span className="label">
                  <User
                    size={14}
                    style={{ marginRight: 4 }}
                  />{' '}
                  작성자
                </span>
                <span className="value">{inquiry.writerName || '익명'}</span>
              </InfoRow>

              {inquiry.storeName && (
                <InfoRow>
                  <span className="label">
                    <Store
                      size={14}
                      style={{ marginRight: 4 }}
                    />{' '}
                    해당 가게
                  </span>
                  <span className="value">{inquiry.storeName}</span>
                </InfoRow>
              )}

              <InfoRow>
                <span className="label">대상 유형</span>
                {/* 💡 ADMIN -> 관리자로 한글 변환 */}
                <span className="value">
                  {TARGET_TYPE_MAP[inquiry.targetType] ||
                    inquiry.targetType ||
                    '-'}
                </span>
              </InfoRow>
            </CardContent>
          </Card>
        </SideColumn>
      </ContentGrid>
    </Container>
  );
}
