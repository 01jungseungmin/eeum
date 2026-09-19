import styled from 'styled-components';
import { Star, Ban, CheckCircle2 } from 'lucide-react';
import { STORE_STATUS_LABEL } from '../../../constants/storeConstants';

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
    width: 80px;
    height: 80px;
    border-radius: 16px;
    background-color: #e8ebee;
    color: #a0a6b5;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 28px;
    font-weight: 700;
    margin-bottom: 16px;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  h2 {
    margin: 0 0 4px 0;
    font-size: 18px;
    font-weight: 700;
    color: #262626;
    text-align: center;
  }

  .category-sub {
    font-size: 13px;
    color: #8c8c8c;
    margin-bottom: 16px;
  }

  .status-badge {
    font-size: 12px;
    font-weight: 700;
    padding: 4px 10px;
    border-radius: 20px;
    background: ${(props) => (props.$status === 'SUSPENDED' ? '#fff1f0' : '#edf5f1')};
    color: ${(props) => (props.$status === 'SUSPENDED' ? '#cf1322' : '#2d5a43')};
    margin-bottom: 24px;
  }

  .rating-row {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #faad14;
    font-size: 14px;
    font-weight: 700;
    margin-bottom: 24px;

    span {
      color: #262626;
    }
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

    &.suspend {
      background: #fff1f0;
      color: #cf1322;
      border-color: #ffccc7;
      &:hover {
        background: #ffe0de;
      }
    }
    &.activate {
      background: #edf5f1;
      color: #2d5a43;
      border-color: #c3e0d1;
      &:hover {
        background: #dcefe4;
      }
    }
  }
`;

function StoreProfileCard({ store, onSuspend, onActivate }) {
  const isSuspended = store.status === 'SUSPENDED';

  return (
    <LeftProfileCard $status={store.status}>
      <div className="avatar">
        {store.images?.find((img) => img.isThumbnail)?.imageUrl ||
        store.images?.[0]?.imageUrl ? (
          <img
            src={
              store.images.find((img) => img.isThumbnail)?.imageUrl ||
              store.images[0].imageUrl
            }
            alt={store.name}
          />
        ) : (
          store.name?.charAt(0)
        )}
      </div>
      <h2>{store.name}</h2>
      <div className="category-sub">{store.categoryName || '-'}</div>
      <div className="status-badge">
        {STORE_STATUS_LABEL[store.status] || store.status}
      </div>

      <div className="rating-row">
        <Star
          size={16}
          fill="currentColor"
        />
        <span>{(store.rating ?? 0).toFixed(1)}</span>
      </div>

      <div className="info-divider" />

      <div className="meta-item">
        <span className="label">주소</span>
        <span className="value">{store.address || '-'}</span>
      </div>
      <div className="meta-item">
        <span className="label">전화번호</span>
        <span className="value">{store.phone || '-'}</span>
      </div>
      <div className="meta-item">
        <span className="label">찜 / 리뷰</span>
        <span className="value">
          {store.favoriteCount ?? 0} / {store.reviewCount ?? 0}
        </span>
      </div>

      {isSuspended ? (
        <button
          className="action-button activate"
          onClick={onActivate}
        >
          <CheckCircle2 size={16} />
          정지 해제
        </button>
      ) : (
        <button
          className="action-button suspend"
          onClick={onSuspend}
        >
          <Ban size={16} />
          상점 정지
        </button>
      )}
    </LeftProfileCard>
  );
}

export default StoreProfileCard;
