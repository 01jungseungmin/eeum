import React from 'react';
import styled from 'styled-components';
import { ChevronRight, AlertCircle } from 'lucide-react';

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

const reviews = [
  {
    id: 1,
    user: '이*민',
    stars: '★★★★★',
    date: '2시간 전',
    text: '정말 맛있어요! 반찬이 집밥 같은 느낌이라 자꾸 찾게 됩니다. 다음에 또 주문할게요!',
    alert: true,
  },
  {
    id: 2,
    user: '정*호',
    stars: '★★★★☆',
    date: '어제',
    text: '양도 많고 맛도 좋아요. 배달도 빠르게 왔어요.',
    alert: false,
  },
  {
    id: 3,
    user: '김*아',
    stars: '★★★★★',
    date: '어제',
    text: '처음 주문했는데 너무 맛있네요. 이제 단골 될 것 같아요!',
    alert: true,
  },
];

function RecentReviews() {
  return (
    <Card>
      <Header>
        <h3>최근 리뷰</h3>
        <span className="more">
          전체 보기 <ChevronRight size={14} />
        </span>
      </Header>
      <div>
        {reviews.map((review) => (
          <ReviewItem key={review.id}>
            <div className="review-top">
              <div className="user-info">
                <span>{review.user}</span>
                <span className="stars">{review.stars}</span>
              </div>
              <span className="date">{review.date}</span>
            </div>
            <div className="content">{review.text}</div>
            {review.alert && (
              <div className="action-needed">
                <AlertCircle size={12} />
                <span>답글 필요</span>
              </div>
            )}
          </ReviewItem>
        ))}
      </div>
    </Card>
  );
}

export default RecentReviews;
