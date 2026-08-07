import React from 'react';
import styled from 'styled-components';
import {
  Star,
  MessageSquare,
  Store,
  User,
  FileText,
  Eye,
  Clock,
  CheckCircle,
} from 'lucide-react';

const CardContainer = styled.div`
  border: 1px solid
    ${(props) => (props.$status === 'PENDING' ? '#fcd34d' : '#e2e8f0')};
  border-radius: 16px;
  background-color: #ffffff;
  padding: 20px 24px;
  margin-bottom: 16px;
`;

const CardContent = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
`;

const MainSection = styled.div`
  display: flex;
  gap: 16px;
  align-items: flex-start;
  flex: 1;
`;

const IconCircle = styled.div`
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  ${(props) => {
    switch (props.$type) {
      case 'STORE_REVIEW':
        return 'background-color: #fef9c3; color: #ca8a04;';
      case 'COMMUNITY_POST':
      case 'COMMUNITY_COMMENT':
        return 'background-color: #e0e7ff; color: #4f46e5;';
      case 'ACCOUNT':
        return 'background-color: #fee2e2; color: #dc2626;';
      case 'STORE':
      default:
        return 'background-color: #dcfce7; color: #15803d;';
    }
  }}
`;

const BodyWrapper = styled.div`
  flex: 1;
`;

const BadgeRow = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
`;

const Badge = styled.span`
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background-color: ${(props) => props.$bg};
  color: ${(props) => props.$color};
`;

const ReportTitle = styled.div`
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 8px;
`;

const ReportDescription = styled.p`
  font-size: 13px;
  color: #64748b;
  margin: 0 0 12px 0;
  line-height: 1.5;
`;

const MetaText = styled.div`
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  gap: 16px;
`;

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
  ETC: '기타 사유',
};

const STATUS_MAP = {
  PENDING: '검토중',
  REVIEWED: '검토 완료',
  DISMISSED: '기각',
};

export default function ReportItem({ item, onOpenDetail }) {
  const renderIcon = () => {
    switch (item.targetType) {
      case 'STORE_REVIEW':
        return <Star size={20} />;
      case 'COMMUNITY_POST':
        return <FileText size={20} />;
      case 'COMMUNITY_COMMENT':
        return <MessageSquare size={20} />;
      case 'ACCOUNT':
        return <User size={20} />;
      default:
        return <Store size={20} />;
    }
  };

  const formattedId = `RPT-${String(item.reportId).padStart(3, '0')}`;
  const formattedDate = item.createdAt ? item.createdAt.split('T')[0] : '';

  return (
    <CardContainer $status={item.status}>
      <CardContent>
        <MainSection>
          <IconCircle $type={item.targetType}>{renderIcon()}</IconCircle>
          <BodyWrapper>
            <BadgeRow>
              <Badge $bg="#fef3c7" $color="#b45309">
                {TARGET_TYPE_MAP[item.targetType] || item.targetType}
              </Badge>
              <Badge $bg="#fee2e2" $color="#dc2626">
                {REASON_MAP[item.reason] || item.reason}
              </Badge>
              <Badge
                $bg={
                  item.status === 'PENDING'
                    ? '#fef3c7'
                    : item.status === 'REVIEWED'
                      ? '#dcfce7'
                      : '#f1f5f9'
                }
                $color={
                  item.status === 'PENDING'
                    ? '#b45309'
                    : item.status === 'REVIEWED'
                      ? '#15803d'
                      : '#64748b'
                }
              >
                {item.status === 'PENDING' && <Clock size={12} />}
                {STATUS_MAP[item.status] || item.status}
              </Badge>
            </BadgeRow>

            <ReportTitle>
              {item.targetType === 'STORE_REVIEW'
                ? `대상 ID: ${item.targetId}`
                : `${TARGET_TYPE_MAP[item.targetType] || '대상'} ID: ${item.targetId}`}
            </ReportTitle>

            <ReportDescription>
              {item.content || '접수된 상세 신고 내용이 없습니다.'}
            </ReportDescription>

            <MetaText>
              <span>신고자: {item.reporterName}</span>
              <span>{formattedDate}</span>
              <span>{formattedId}</span>
            </MetaText>
          </BodyWrapper>
        </MainSection>

        {/* 클릭 시 모달 오픈 함수 실행 */}
        <Eye
          size={20}
          color="#6366f1"
          style={{ cursor: 'pointer', marginLeft: '12px' }}
          onClick={() => onOpenDetail(item.reportId)}
        />
      </CardContent>
    </CardContainer>
  );
}
