import styled from 'styled-components';
import { clickableCardStyle } from '../../common/cardFilterStyle';

const SummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(1, 1fr);
  gap: 16px;

  @media (min-width: 768px) {
    grid-template-columns: repeat(4, 1fr);
  }
`;

const SummaryCard = styled.div`
  background-color: ${(props) => props.$bg};
  border: 1px solid ${(props) => props.$borderColor};
  border-radius: 16px;
  padding: 20px;
  ${clickableCardStyle}
`;

const CardLabel = styled.div`
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 8px;
`;

const CardValue = styled.div`
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 4px;
`;

const CardSubText = styled.div`
  font-size: 12px;
  font-weight: 500;
  color: ${(props) => props.$color};
`;

// 카드 하나 = 백엔드 InquiryStatus 하나 (전체는 상태 필터 없음)
const CARDS = [
  {
    status: 'ALL',
    label: '전체 문의',
    subText: '누적 접수',
    $bg: '#EFF6FF',
    $borderColor: '#BFDBFE',
    $color: '#2563EB',
  },
  {
    status: 'PENDING',
    label: '처리 대기',
    subText: '답변을 기다리는 문의',
    $bg: '#FEFCE8',
    $borderColor: '#FEF08A',
    $color: '#D97706',
  },
  {
    status: 'ANSWERED',
    label: '답변 완료',
    subText: '답변이 등록된 문의',
    $bg: '#F0FDF4',
    $borderColor: '#BBF7D0',
    $color: '#16A34A',
  },
  {
    status: 'CLOSED',
    label: '종료됨',
    subText: '종료 처리된 문의',
    $bg: '#FAF5FF',
    $borderColor: '#E9D5FF',
    $color: '#9333EA',
  },
];

// counts: { PENDING, ANSWERED, CLOSED } (아직 못 불러왔으면 null)
export default function InquirySummaryCards({
  counts,
  selectedStatus,
  onSelect,
}) {
  const valueOf = (status) => {
    if (!counts) return '-';
    if (status === 'ALL') {
      return counts.PENDING + counts.ANSWERED + counts.CLOSED;
    }
    return counts[status];
  };

  return (
    <SummaryGrid>
      {CARDS.map((card) => (
        <SummaryCard
          key={card.status}
          $bg={card.$bg}
          $borderColor={card.$borderColor}
          $clickable={Boolean(onSelect)}
          $active={selectedStatus === card.status}
          onClick={() => onSelect?.(card.status)}
        >
          <CardLabel>{card.label}</CardLabel>
          <CardValue>{valueOf(card.status)}</CardValue>
          <CardSubText $color={card.$color}>{card.subText}</CardSubText>
        </SummaryCard>
      ))}
    </SummaryGrid>
  );
}
