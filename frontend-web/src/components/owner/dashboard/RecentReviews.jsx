import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { ChevronRight, AlertCircle } from 'lucide-react';
import { reviewApi } from '../../../api/owner/reviewApi';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
  }
  .more {
    display: flex;
    align-items: center;
    font-size: 12px;
    color: #52c41a;
    cursor: pointer;
    font-weight: 600;
  }
`;

const ReviewItem = styled.div`
  border-bottom: 1px solid #f5f5f5;
  padding: 12px 0;
  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }
  &:first-child {
    padding-top: 0;
  }

  .review-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .user-info {
    font-size: 13px;
    font-weight: 600;
    color: #262626;
    display: flex;
    gap: 8px;
  }
  .stars {
    color: #faad14;
    font-size: 12px;
  }
  .date {
    font-size: 11px;
    color: #bfbfbf;
  }
  .content {
    font-size: 12px;
    color: #595959;
    margin: 8px 0;
    line-height: 1.5;
  }
  .action-needed {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #ff4d4f;
    font-size: 11px;
    font-weight: 600;
  }
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 30px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

// 상대 시간 포맷 (NotificationItem.jsx의 formatTimeAgo와 동일한 규칙)
const formatTimeAgo = (dateString) => {
  if (!dateString) return '';
  const diffMinutes = Math.floor((new Date() - new Date(dateString)) / 60000);

  if (diffMinutes < 1) return '방금 전';
  if (diffMinutes < 60) return `${diffMinutes}분 전`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}시간 전`;

  return `${Math.floor(diffHours / 24)}일 전`;
};

const toStars = (rating) => '★'.repeat(rating) + '☆'.repeat(5 - rating);

function RecentReviews() {
  const navigate = useNavigate();
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchRecentReviews = async () => {
    try {
      setLoading(true);
      const response = await reviewApi.getStoreReviews(0, 3);

      if (response.data?.success) {
        setReviews(response.data.data.content || []);
      }
    } catch (error) {
      console.error('최근 리뷰 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    queueMicrotask(() => fetchRecentReviews());
  }, []);

  return (
    <Card>
      <Header>
        <h3>최근 리뷰</h3>
        <span
          className="more"
          onClick={() => navigate('/reviews')}
        >
          전체 보기 <ChevronRight size={14} />
        </span>
      </Header>
      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : reviews.length === 0 ? (
        <EmptyText>최근 리뷰가 없습니다.</EmptyText>
      ) : (
        <div>
          {reviews.map((review) => (
            <ReviewItem key={review.storereviewId}>
              <div className="review-top">
                <div className="user-info">
                  <span>{review.nickname}</span>
                  <span className="stars">{toStars(review.rating)}</span>
                </div>
                <span className="date">{formatTimeAgo(review.createdAt)}</span>
              </div>
              <div className="content">{review.content}</div>
              {!review.hasReply && (
                <div className="action-needed">
                  <AlertCircle size={12} />
                  <span>답글 필요</span>
                </div>
              )}
            </ReviewItem>
          ))}
        </div>
      )}
    </Card>
  );
}

export default RecentReviews;
