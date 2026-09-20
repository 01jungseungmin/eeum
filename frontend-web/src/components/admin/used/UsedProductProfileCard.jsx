import styled from 'styled-components';
import { EyeOff, Eye } from 'lucide-react';
import {
  USED_PRODUCT_STATUS_LABEL,
  formatUsedProductPrice,
} from '../../../constants/usedProductConstants';

const LeftProfileCard = styled.div`
  background: white;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  padding: 32px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);

  .thumb {
    width: 120px;
    height: 120px;
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
    font-size: 17px;
    font-weight: 700;
    color: #262626;
    text-align: center;
  }

  .price {
    font-size: 15px;
    font-weight: 700;
    color: #2d5a43;
    margin-bottom: 16px;
  }

  .status-badge {
    font-size: 12px;
    font-weight: 700;
    padding: 4px 10px;
    border-radius: 20px;
    background: ${(props) =>
      props.$status === 'SOLD'
        ? '#f5f5f5'
        : props.$status === 'RESERVED'
          ? '#fffbe6'
          : '#edf5f1'};
    color: ${(props) =>
      props.$status === 'SOLD'
        ? '#8c8c8c'
        : props.$status === 'RESERVED'
          ? '#ad6800'
          : '#2d5a43'};
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

function UsedProductProfileCard({ product, onHide, onShow }) {
  const thumbnail = product.images?.find((img) => img.thumbnail)?.imageUrl;

  return (
    <LeftProfileCard $status={product.status}>
      <div className="thumb">
        {thumbnail ? (
          <img
            src={thumbnail}
            alt={product.title}
          />
        ) : (
          product.title?.charAt(0)
        )}
      </div>
      <h2>{product.title}</h2>
      <div className="price">
        {formatUsedProductPrice(product.priceType, product.price)}
      </div>
      <div className="status-badge">
        {USED_PRODUCT_STATUS_LABEL[product.status] || product.status}
      </div>

      <div className="info-divider" />

      <div className="meta-item">
        <span className="label">판매자</span>
        <span className="value">{product.sellerNickname || '-'}</span>
      </div>
      <div className="meta-item">
        <span className="label">카테고리</span>
        <span className="value">{product.categoryName || '-'}</span>
      </div>
      <div className="meta-item">
        <span className="label">동네</span>
        <span className="value">{product.regionName || '-'}</span>
      </div>
      <div className="meta-item">
        <span className="label">찜 / 조회</span>
        <span className="value">
          {product.favoriteCount ?? 0} / {product.viewCount ?? 0}
        </span>
      </div>

      {product.hidden ? (
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

export default UsedProductProfileCard;
