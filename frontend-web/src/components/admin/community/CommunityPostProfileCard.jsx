import styled from 'styled-components';
import { EyeOff, Eye } from 'lucide-react';

const LeftProfileCard = styled.div`
  background: white;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  padding: 32px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);

  .avatar {
    width: 64px;
    height: 64px;
    border-radius: 50%;
    background-color: #e8ebee;
    color: #a0a6b5;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    font-weight: 700;
    margin-bottom: 12px;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .author {
    font-size: 15px;
    font-weight: 700;
    color: #262626;
    margin-bottom: 4px;
  }

  .category {
    font-size: 13px;
    color: #8c8c8c;
    margin-bottom: 16px;
  }

  .status-badge {
    font-size: 12px;
    font-weight: 700;
    padding: 4px 10px;
    border-radius: 20px;
    background: ${(props) => (props.$hidden ? '#fff1f0' : '#edf5f1')};
    color: ${(props) => (props.$hidden ? '#cf1322' : '#2d5a43')};
    margin-bottom: 24px;
  }

  .info-divider {
    width: 100%;
    height: 1px;
    background-color: #f0f0f0;
    margin-bottom: 20px;
  }

  .meta-item {
    width: 100%;
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    margin-bottom: 12px;

    &:last-child {
      margin-bottom: 0;
    }

    .label {
      color: #8c8c8c;
    }
    .value {
      color: #262626;
      font-weight: 500;
      text-align: right;
    }
  }

  .action-button {
    width: 100%;
    margin-top: 24px;
    padding: 11px 0;
    border-radius: 6px;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    border: 1px solid transparent;
    transition: all 0.15s ease;

    &.hide {
      background: #fff1f0;
      color: #cf1322;
      border-color: #ffccc7;
      &:hover {
        background: #ffe0de;
      }
    }
    &.show {
      background: #edf5f1;
      color: #2d5a43;
      border-color: #c3e0d1;
      &:hover {
        background: #dcefe4;
      }
    }
  }
`;

function CommunityPostProfileCard({ post, onHide, onShow }) {
  return (
    <LeftProfileCard $hidden={post.hidden}>
      <div className="avatar">
        {post.authorProfileImageUrl ? (
          <img
            src={post.authorProfileImageUrl}
            alt={post.authorNickname}
          />
        ) : (
          post.authorNickname?.charAt(0)
        )}
      </div>
      <div className="author">{post.authorNickname}</div>
      <div className="category">{post.categoryName}</div>
      <div className="status-badge">
        {post.hidden ? '숨김 처리됨' : '정상 노출 중'}
      </div>

      <div className="info-divider" />

      <div className="meta-item">
        <span className="label">지역</span>
        <span className="value">{post.regionName || '-'}</span>
      </div>
      <div className="meta-item">
        <span className="label">조회수</span>
        <span className="value">{post.viewCount ?? 0}</span>
      </div>
      <div className="meta-item">
        <span className="label">좋아요</span>
        <span className="value">{post.likeCount ?? 0}</span>
      </div>
      <div className="meta-item">
        <span className="label">댓글</span>
        <span className="value">{post.commentCount ?? 0}</span>
      </div>

      {post.hidden ? (
        <button
          className="action-button show"
          onClick={onShow}
        >
          <Eye size={16} />
          숨김 해제
        </button>
      ) : (
        <button
          className="action-button hide"
          onClick={onHide}
        >
          <EyeOff size={16} />
          게시글 숨김
        </button>
      )}
    </LeftProfileCard>
  );
}

export default CommunityPostProfileCard;
