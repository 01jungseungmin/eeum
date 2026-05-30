import React from 'react';
import styled from 'styled-components';
import { Camera, Store, Check, Star, Heart, MessageSquare } from 'lucide-react';

const Card = styled.div`
  background: white;
  border-radius: 16px;
  border: 1px solid #e8e8e8;
  overflow: hidden;
`;

const ImageContainer = styled.div`
  position: relative;
  width: 100%;
  height: 140px;
  border-radius: 12px;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
  background-color: #2d5a43; /* 이미지가 없을 때를 대비한 백업 톤앤매너 컬러 */

  .no-image {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    color: rgba(255, 255, 255, 0.8);
    font-size: 13px;
    font-weight: 500;
  }
`;

const StoreIcon = styled(Store)`
  width: 40px;
  height: 40px;
  color: white;
  background: rgba(255, 255, 255, 0.2);
  padding: 8px;
  border-radius: 12px;
`;

const CameraButton = styled.button`
  position: absolute;
  bottom: 12px;
  right: 12px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(4px);
  border: none;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: rgba(255, 255, 255, 0.5);
  }
`;

const ProfileContent = styled.div`
  padding: 20px;
`;

const TitleBar = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
`;

const StoreName = styled.h2`
  font-size: 20px;
  font-weight: 700;
  margin: 0;
`;

const StatusBadge = styled.span`
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  background: ${(props) =>
    props.$mode === 'OPERATING'
      ? '#e8f5e9'
      : props.$mode === 'CLOSED'
        ? '#fff1f0'
        : '#fffbe6'};
  color: ${(props) =>
    props.$mode === 'OPERATING'
      ? '#2e7d32'
      : props.$mode === 'CLOSED'
        ? '#f5222d'
        : '#d46b08'};
`;

const Desc = styled.p`
  font-size: 13px;
  color: #666;
  line-height: 1.5;
  margin: 12px 0;
`;

// 💡 백엔드 신규 통계 데이터(평점/찜/리뷰) 가시성을 극대화하기 위한 스타일 레이아웃
const StatsSummaryBar = styled.div`
  display: flex;
  gap: 12px;
  margin: 12px 0;
  padding: 8px 0;
  border-top: 1px dashed #f1f3f5;
  border-bottom: 1px dashed #f1f3f5;
`;

const MiniStatItem = styled.div`
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: #555;
  font-weight: 500;
`;

const StatusSelectArea = styled.div`
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const StatusOptionCard = styled.div`
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid ${(props) => (props.$active ? props.$color : '#e8e8e8')};
  background: ${(props) => (props.$active ? props.$bgColor : 'white')};
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s;
`;

function StoreProfileCard({
  storeInfo,
  thumbnailUrl,
  onOpenImageModal,
  onStatusChange,
}) {
  if (!storeInfo) return null;

  const getStatusLabel = (status) => {
    if (status === 'OPERATING' || status === 'OPEN') return '영업중';
    if (status === 'CLOSED') return '영업 종료';
    return '휴식중';
  };

  return (
    <Card>
      <ImageContainer
        style={{
          backgroundImage: thumbnailUrl ? `url(${thumbnailUrl})` : 'none',
        }}
      >
        {!thumbnailUrl && (
          <div className="no-image">
            <StoreIcon />
            <span>등록된 상점 대표 이미지가 없습니다</span>
          </div>
        )}
        <CameraButton onClick={onOpenImageModal} title="상점 사진 관리">
          <Camera size={16} color="white" />
        </CameraButton>
      </ImageContainer>

      <ProfileContent>
        <TitleBar>
          <StoreName>{storeInfo.name}</StoreName>
          <StatusBadge $mode={storeInfo.status}>
            {getStatusLabel(storeInfo.status)}
          </StatusBadge>
        </TitleBar>

        <div style={{ fontSize: '13px', color: '#999', fontWeight: '500' }}>
          {storeInfo.category}
        </div>

        <Desc>{storeInfo.description}</Desc>

        {/* 💡 [신규 추가 연동] 백엔드가 내려주는 평점, 찜, 리뷰 데이터 파싱 바인딩 */}
        <StatsSummaryBar>
          <MiniStatItem>
            <Star size={13} fill="#ffbc00" color="#ffbc00" />
            <span>{storeInfo.rating?.toFixed(1) || '0.0'}</span>
          </MiniStatItem>
          <MiniStatItem>
            <Heart size={13} fill="#ff4d61" color="#ff4d61" />
            <span>찜 {storeInfo.favoriteCount || 0}</span>
          </MiniStatItem>
          <MiniStatItem>
            <MessageSquare size={13} color="#4dabf7" />
            <span>리뷰 {storeInfo.reviewCount || 0}</span>
          </MiniStatItem>
        </StatsSummaryBar>

        <div
          style={{
            fontSize: '13px',
            color: '#666',
            marginTop: '12px',
            display: 'flex',
            gap: '4px',
          }}
        >
          <span>📍</span> <span>{storeInfo.address}</span>
        </div>
        <div
          style={{
            fontSize: '13px',
            color: '#666',
            marginTop: '6px',
            display: 'flex',
            gap: '4px',
          }}
        >
          <span>📞</span> <span>{storeInfo.phone}</span>
        </div>

        <StatusSelectArea>
          <h4 style={{ margin: '0 0 4px 0', fontSize: '14px', color: '#333' }}>
            영업 상태 변경
          </h4>

          <StatusOptionCard
            $active={storeInfo.status === 'OPERATING'}
            $color="#2d5a43"
            $bgColor="#e8f5e9"
            onClick={() => onStatusChange('OPERATING')}
          >
            <span style={{ color: '#2e7d32' }}>● 영업중</span>
            {storeInfo.status === 'OPERATING' && (
              <Check size={16} color="#2e7d32" />
            )}
          </StatusOptionCard>

          <StatusOptionCard
            $active={storeInfo.status === 'CLOSED'}
            $color="#f5222d"
            $bgColor="#fff1f0"
            onClick={() => onStatusChange('CLOSED')}
          >
            <span style={{ color: '#f5222d' }}>● 영업 종료</span>
            {storeInfo.status === 'CLOSED' && (
              <Check size={16} color="#f5222d" />
            )}
          </StatusOptionCard>

          <StatusOptionCard
            $active={storeInfo.status === 'BREAK'}
            $color="#d46b08"
            $bgColor="#fffbe6"
            onClick={() => onStatusChange('BREAK')}
          >
            <span style={{ color: '#d46b08' }}>● 휴식중</span>
            {storeInfo.status === 'BREAK' && (
              <Check size={16} color="#d46b08" />
            )}
          </StatusOptionCard>
        </StatusSelectArea>
      </ProfileContent>
    </Card>
  );
}

export default StoreProfileCard;
