import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import {
  ArrowLeft,
  ChevronDown,
  AlertTriangle,
  CheckCircle2,
} from 'lucide-react';
import { reportApi } from '../../../api/admin/reportApi';
import {
  TARGET_TYPE_MAP,
  REASON_MAP,
  STATUS_MAP,
  ACTION_OPTIONS,
} from '../../../constants/reportConstants';

const Container = styled.div`
  margin: 0 auto;
  padding: 32px;
`;

const LoadingWrapper = styled.div`
  padding: 80px 0;
  text-align: center;
  color: #6b7280;
  font-size: 15px;
`;

const EmptyWrapper = styled.div`
  padding: 80px 0;
  text-align: center;
  color: #374151;
  p {
    margin-bottom: 16px;
  }
`;

const HeaderNav = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  h1 {
    font-size: 20px;
    font-weight: 700;
    color: #111827;
    margin: 0;
  }
  p {
    font-size: 13px;
    color: #6b7280;
    margin: 2px 0 0 0;
  }
`;

const BackButton = styled.button`
  border: 1px solid #e5e7eb;
  background: white;
  border-radius: 8px;
  padding: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #374151;
  &:hover {
    background: #f9fafb;
  }
`;

const DetailGrid = styled.div`
  display: grid;
  grid-template-columns: 2.2fr 1fr;
  gap: 24px;
`;

const LeftSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

const RightSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

const SectionCard = styled.div`
  background: white;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  padding: 24px;
  h2 {
    font-size: 16px;
    font-weight: 700;
    margin: 0 0 20px 0;
    color: #111827;
  }
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h2 {
    margin: 0;
  }
`;

const StatusBadge = styled.span`
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  background: ${(props) => {
    if (props.$status === 'PENDING') return '#FEF08A';
    if (props.$status === 'REVIEWED') return '#BBF7D0';
    if (props.$status === 'DISMISSED') return '#E5E7EB';
    return '#F3F4F6';
  }};
  color: ${(props) => {
    if (props.$status === 'PENDING') return '#854D0E';
    if (props.$status === 'REVIEWED') return '#166534';
    if (props.$status === 'DISMISSED') return '#374151';
    return '#374151';
  }};
`;

const InfoGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 20px;
`;

const InfoGroup = styled.div`
  label {
    font-size: 12px;
    color: #6b7280;
    display: block;
    margin-bottom: 4px;
  }
  div {
    font-size: 14px;
    color: #374151;
  }
  .bold {
    font-weight: 600;
    color: #111827;
  }
`;

const ContentGroup = styled.div`
  label {
    font-size: 12px;
    color: #6b7280;
    display: block;
    margin-bottom: 8px;
  }
`;

const ContentBox = styled.div`
  background: #f9fafb;
  border: 1px solid #f3f4f6;
  padding: 16px;
  border-radius: 8px;
  font-size: 14px;
  color: #374151;
  line-height: 1.5;
  white-space: pre-wrap;
`;

const FormGroup = styled.div`
  margin-bottom: 20px;
  label {
    font-size: 13px;
    font-weight: 600;
    color: #374151;
    display: block;
    margin-bottom: 8px;
  }
`;

const CustomSelectContainer = styled.div`
  position: relative;
`;

const SelectHeader = styled.div`
  background: #f3f4f6;
  padding: 12px 16px;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
`;

const SelectText = styled.span`
  font-size: 14px;
  color: ${(props) => (props.$hasValue ? '#374151' : '#9CA3AF')};
`;

const SelectList = styled.div`
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  z-index: 10;
  padding: 4px;
`;

const SelectItem = styled.div`
  padding: 10px 12px;
  font-size: 14px;
  border-radius: 6px;
  cursor: pointer;
  color: #374151;
  background: ${(props) => (props.$isSelected ? '#f3f4f6' : 'transparent')};
  &:hover {
    background: #f9fafb;
  }
`;

const AlertNotice = styled.div`
  background: #fffbe3;
  border: 1px solid #fef08a;
  padding: 10px 12px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #b45309;
  margin-bottom: 10px;
`;

const TextArea = styled.textarea`
  width: 100%;
  height: 100px;
  background: #f3f4f6;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 12px;
  box-sizing: border-box;
  outline: none;
  font-size: 14px;
  resize: none;
  &:focus {
    background: white;
    border-color: #d1d5db;
  }
`;

const ButtonGroup = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
`;

const Button = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border-radius: 8px;
  border: ${(props) => (props.$primary ? 'none' : '1px solid #e5e7eb')};
  background: ${(props) => (props.$primary ? '#059669' : 'white')};
  color: ${(props) => (props.$primary ? 'white' : '#374151')};
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
  &:hover:not(:disabled) {
    opacity: 0.9;
  }
`;

const SideRow = styled.div`
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #f3f4f6;
  font-size: 14px;
  &:last-child {
    border-bottom: none;
  }
  span {
    color: #6b7280;
  }
  strong {
    color: #111827;
  }
`;

const ReportDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [status, setStatus] = useState('');
  const [adminNote, setAdminNote] = useState('');
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  useEffect(() => {
    const fetchDetail = async () => {
      setLoading(true);
      try {
        const res = await reportApi.getReportDetail(id);
        const data = res.data || res;
        if (data.success) {
          setReport(data.data);
          setStatus('');
          setAdminNote(data.data.adminNote || '');
        }
      } catch (error) {
        console.error('신고 상세 조회 실패:', error);
      } finally {
        setLoading(false);
      }
    };

    if (id) fetchDetail();
  }, [id]);

  // 신고 조치 저장
  const handleSubmit = async () => {
    if (!status) {
      alert('처리 조치를 선택해주세요.');
      return;
    }

    setIsSubmitting(true);
    try {
      let res;

      // '신고 기각'인 경우 /dismiss 호출, 그 외(숨김, 삭제, 경고, 정지 등)는 /review 호출
      if (status === 'DISMISS' || status === 'DISMISSED') {
        res = await reportApi.dismissReport(id, adminNote);
      } else {
        res = await reportApi.reviewReport(id, adminNote);
      }

      const responseData = res.data || res;
      if (responseData.success) {
        alert('신고 처리가 성공적으로 완료되었습니다.');
        navigate('/admin/reports');
      } else {
        alert(responseData.message || '처리에 실패했습니다.');
      }
    } catch (error) {
      console.error('신고 처리 실패:', error);
      alert('처리에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (loading) {
    return <LoadingWrapper>상세 정보를 불러오는 중입니다...</LoadingWrapper>;
  }

  if (!report) {
    return (
      <EmptyWrapper>
        <p>해당 신고 정보를 찾을 수 없습니다.</p>
        <Button onClick={() => navigate(-1)}>목록으로 돌아가기</Button>
      </EmptyWrapper>
    );
  }

  return (
    <Container>
      <HeaderNav>
        <BackButton onClick={() => navigate(-1)}>
          <ArrowLeft size={20} />
        </BackButton>
        <div>
          <h1>신고 상세 및 조치</h1>
          <p>신고 ID #{report.reportId}</p>
        </div>
      </HeaderNav>

      <DetailGrid>
        <LeftSection>
          <SectionCard>
            <CardHeader>
              <h2>신고 내역</h2>
              <StatusBadge $status={report.status}>
                {STATUS_MAP[report.status] || report.status}
              </StatusBadge>
            </CardHeader>

            <InfoGrid>
              <InfoGroup>
                <label>신고 대상 유형</label>
                <div className="bold">
                  {TARGET_TYPE_MAP[report.targetType] || report.targetType}
                </div>
              </InfoGroup>
              <InfoGroup>
                <label>신고 사유</label>
                <div className="bold">
                  {REASON_MAP[report.reason] || report.reason}
                </div>
              </InfoGroup>
              <InfoGroup>
                <label>접수 일시</label>
                <div>{formatDate(report.createdAt)}</div>
              </InfoGroup>
              <InfoGroup>
                <label>최종 수정 일시</label>
                <div>{formatDate(report.updatedAt)}</div>
              </InfoGroup>
            </InfoGrid>

            <ContentGroup>
              <label>신고 상세 내용</label>
              <ContentBox>
                {report.content || '작성된 내용이 없습니다.'}
              </ContentBox>
            </ContentGroup>
          </SectionCard>

          <SectionCard>
            <h2>조치 및 메모 입력</h2>

            <FormGroup>
              <label>처리 상태 선택</label>
              <CustomSelectContainer>
                <SelectHeader
                  onClick={() => setIsDropdownOpen(!isDropdownOpen)}
                >
                  <SelectText $hasValue={!!status}>
                    {ACTION_OPTIONS.find((opt) => opt.value === status)
                      ?.label || '조치를 선택하세요'}
                  </SelectText>
                  <ChevronDown
                    size={18}
                    color="#6B7280"
                  />
                </SelectHeader>

                {isDropdownOpen && (
                  <SelectList>
                    {ACTION_OPTIONS.map((opt) => (
                      <SelectItem
                        key={opt.value}
                        $isSelected={status === opt.value}
                        onClick={() => {
                          setStatus(opt.value);
                          setIsDropdownOpen(false);
                        }}
                      >
                        {opt.label}
                      </SelectItem>
                    ))}
                  </SelectList>
                )}
              </CustomSelectContainer>
            </FormGroup>

            <FormGroup>
              <label>관리자 메모</label>
              <AlertNotice>
                <AlertTriangle
                  size={16}
                  color="#D97706"
                />
                <span>처리 사유 및 조치 내역을 상세히 남겨주세요.</span>
              </AlertNotice>
              <TextArea
                placeholder="처리 관련 관리자 메모를 입력하세요..."
                value={adminNote}
                onChange={(e) => setAdminNote(e.target.value)}
              />
            </FormGroup>

            <ButtonGroup>
              <Button
                type="button"
                onClick={() => navigate(-1)}
              >
                취소
              </Button>
              <Button
                $primary
                type="button"
                disabled={isSubmitting}
                onClick={handleSubmit}
              >
                <CheckCircle2 size={16} />
                {isSubmitting ? '저장 중...' : '저장하기'}
              </Button>
            </ButtonGroup>
          </SectionCard>
        </LeftSection>

        <RightSection>
          <SectionCard>
            <h2>신고자 정보</h2>
            <SideRow>
              <span>신고자 ID</span>
              <strong>#{report.reporterId}</strong>
            </SideRow>
            <SideRow>
              <span>신고자 이름</span>
              <strong>{report.reporterName}</strong>
            </SideRow>
          </SectionCard>

          <SectionCard>
            <h2>신고 대상 정보</h2>
            <SideRow>
              <span>대상 ID</span>
              <strong>#{report.targetId}</strong>
            </SideRow>
            <SideRow>
              <span>대상 유형</span>
              <strong>
                {TARGET_TYPE_MAP[report.targetType] || report.targetType}
              </strong>
            </SideRow>
          </SectionCard>
        </RightSection>
      </DetailGrid>
    </Container>
  );
};

export default ReportDetailPage;
