import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { X, ShoppingCart, Calendar, BookOpen, Star } from 'lucide-react';
import { productApi } from '../../../api/owner/productApi';

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1100;
`;

const ModalContainer = styled.div`
  background: white;
  border-radius: 24px;
  width: 520px;
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.12);
  overflow: hidden;
  position: relative;
`;

const ScrollContent = styled.div`
  padding: 30px;
  max-height: 85vh;
  overflow-y: auto;

  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: #cbd5e1;
    border-radius: 10px;
  }
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  h2 {
    margin: 0;
    font-size: 18px;
    font-weight: bold;
    color: #1a1f2c;
  }
  .close-icon {
    cursor: pointer;
    color: #8e94a0;
    &:hover {
      color: #333;
    }
  }
`;

const ImageList = styled.div`
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
`;

const ImageBox = styled.div`
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 12px;
  border: 1px solid ${(props) => (props.$main ? '#00a651' : '#eef0f2')};
  padding: 2px;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 10px;
  }
  .main-badge {
    position: absolute;
    top: -6px;
    left: -6px;
    background: #00a651;
    color: white;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
  }
`;

const BadgeGroup = styled.div`
  display: flex;
  gap: 8px;
  margin-bottom: 25px;
`;

const TypeBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  background: ${(props) => props.$bg};
  color: ${(props) => props.$color};
`;

const StatusBadge = styled.span`
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: bold;
  background: ${(props) => props.$bg};
  color: ${(props) => props.$color};
`;

const InfoGrid = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 30px;
`;

const InfoRow = styled.div`
  display: flex;
  font-size: 13px;
  line-height: 1.5;

  .label {
    width: 90px;
    color: #8e94a0;
    font-weight: 500;
  }
  .value {
    flex: 1;
    color: #1a1f2c;
  }
  .value.bold {
    font-weight: bold;
  }
`;

const OptionSection = styled.div`
  margin-bottom: 25px;
  .title {
    font-size: 13px;
    font-weight: bold;
    color: #333;
    margin-bottom: 12px;
  }
`;

const OptionGroupCard = styled.div`
  background: #f8f9fa;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 10px;

  .group-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
  }
  .group-name {
    font-size: 12px;
    font-weight: bold;
    color: #495057;
  }
  .group-badge {
    font-size: 10px;
    padding: 2px 6px;
    background: #eef2ff;
    color: #4361ee;
    border-radius: 4px;
    font-weight: 600;
  }
  .chips {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
`;

const OptionChip = styled.span`
  background: white;
  border: 1px solid #e9ecef;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 11px;
  color: #495057;
  display: inline-flex;
  align-items: center;

  .price {
    color: #00a651;
    margin-left: 4px;
    font-weight: 600;
  }
  .default-tag {
    color: #8e94a0;
    margin-left: 4px;
    font-size: 10px;
  }
`;

const FooterButton = styled.button`
  width: 100%;
  padding: 14px;
  background: white;
  border: 1px solid #e9ecef;
  border-radius: 12px;
  color: #333;
  font-size: 13px;
  font-weight: bold;
  cursor: pointer;
  transition: background 0.2s;
  &:hover {
    background: #f8f9fa;
  }
  margin-top: 10px;
`;

function ProductDetailModal({ productId, onClose }) {
  const [data, setData] = useState(null);
  const [images, setImages] = useState([]);
  const [productOptions, setProductOptions] = useState([]); // 🆕 옵션 상태값 추가
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchAllData = async () => {
      try {
        setLoading(true);

        const [detailResponse, imageResponse, optionResponse] =
          await Promise.all([
            productApi.getOwnerProductDetail(productId),
            productApi.getProductImages(productId),
            productApi.getProductOptions
              ? productApi.getProductOptions(productId)
              : null,
          ]);

        if (detailResponse.data && detailResponse.data.success) {
          setData(detailResponse.data.data);
        } else {
          alert(
            detailResponse.data.message || '상세 정보를 불러오지 못했습니다.',
          );
          onClose();
          return;
        }

        if (imageResponse.data && imageResponse.data.success) {
          const rawImages = imageResponse.data.data || [];
          const sortedImages = [
            ...rawImages.filter((img) => img.thumbnail),
            ...rawImages
              .filter((img) => !img.thumbnail)
              .sort((a, b) => a.displayOrder - b.displayOrder),
          ];
          setImages(sortedImages);
        }

        if (
          optionResponse &&
          optionResponse.data &&
          optionResponse.data.success
        ) {
          setProductOptions(optionResponse.data.data || []);
        }
      } catch (error) {
        console.error('상품 데이터 로드 실패:', error);
        alert('서버 통신 중 오류가 발생했습니다.');
        onClose();
      } finally {
        setLoading(false);
      }
    };
    fetchAllData();
  }, [productId, onClose]);

  if (loading) {
    return (
      <ModalOverlay onClick={onClose}>
        <ModalContainer onClick={(e) => e.stopPropagation()}>
          <div
            style={{
              padding: '60px',
              textAlign: 'center',
              color: '#999',
              fontSize: '14px',
            }}
          >
            🔄 상품 상세 정보를 가져오는 중입니다...
          </div>
        </ModalContainer>
      </ModalOverlay>
    );
  }

  if (!data) return null;

  const typeStyles = {
    SALE: {
      text: '판매 상품',
      bg: '#eef2ff',
      color: '#4361ee',
      icon: <ShoppingCart size={13} strokeWidth={2.5} />,
    },
    MENU: {
      text: '메뉴 상품',
      bg: '#f1f3f5',
      color: '#666',
      icon: <BookOpen size={13} strokeWidth={2.5} />,
    },
  }[data.productType] || {
    text: data.productType,
    bg: '#f1f3f5',
    color: '#666',
    icon: null,
  };

  const statusStyles = {
    ACTIVE: { text: '판매중', bg: '#e6f7ed', color: '#00a651' },
    SOLD_OUT: { text: '품절', bg: '#ffebee', color: '#ff4d4d' },
    INACTIVE: { text: '비공개', bg: '#f1f3f5', color: '#666' },
  }[data.status] || { text: data.status, bg: '#f1f3f5', color: '#666' };

  return (
    <ModalOverlay onClick={onClose}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <ScrollContent>
          <Header>
            <h2>상품 상세 정보</h2>
            <X className="close-icon" size={20} onClick={onClose} />
          </Header>

          <ImageList>
            {images.length > 0 ? (
              images.map((img) => (
                <ImageBox key={img.imageId} $main={img.thumbnail}>
                  {img.thumbnail && (
                    <div className="main-badge">
                      <Star size={10} fill="white" color="white" />
                    </div>
                  )}
                  <img
                    src={img.imageUrl}
                    alt={`상품 이미지 ${img.displayOrder}`}
                  />
                </ImageBox>
              ))
            ) : (
              <div
                style={{ fontSize: '12px', color: '#999', padding: '10px 0' }}
              >
                등록된 이미지가 없습니다.
              </div>
            )}
          </ImageList>

          <BadgeGroup>
            <TypeBadge $bg={typeStyles.bg} $color={typeStyles.color}>
              {typeStyles.icon}
              {typeStyles.text}
            </TypeBadge>
            <StatusBadge $bg={statusStyles.bg} $color={statusStyles.color}>
              {statusStyles.text}
            </StatusBadge>
          </BadgeGroup>

          <InfoGrid>
            <InfoRow>
              <div className="label">상품명</div>
              <div className="value bold">{data.name}</div>
            </InfoRow>
            <InfoRow>
              <div className="label">카테고리</div>
              <div className="value">{data.categoryName}</div>
            </InfoRow>
            <InfoRow>
              <div className="label">가격</div>
              <div className="value bold">
                {data.productType === 'MENU'
                  ? '조회만 가능'
                  : `${data.price?.toLocaleString() || 0}원`}
              </div>
            </InfoRow>
            <InfoRow>
              <div className="label">재고</div>
              <div className="value bold">
                {data.productType === 'MENU'
                  ? '미설정'
                  : `${data.stock || 0}개`}
              </div>
            </InfoRow>
            <InfoRow>
              <div className="label">조회수</div>
              <div className="value bold">{data.viewCount || 0}회</div>
            </InfoRow>

            {data.pickupTime && (
              <InfoRow>
                <div className="label">픽업 시간</div>
                <div className="value bold">{data.pickupTime}</div>
              </InfoRow>
            )}
            <InfoRow>
              <div className="label">설명</div>
              <div className="value" style={{ color: '#666' }}>
                {data.description}
              </div>
            </InfoRow>
          </InfoGrid>

          {data.productType === 'SALE' &&
            productOptions &&
            productOptions.length > 0 && (
              <OptionSection>
                <div className="title">
                  상품 옵션 ({productOptions.length}개)
                </div>
                {productOptions.map((group, gIdx) => (
                  <OptionGroupCard key={group.optionGroupId || gIdx}>
                    <div className="group-header">
                      <div className="group-name">
                        {group.groupName || group.name}
                      </div>
                      {group.isRequired && (
                        <span className="group-badge">필수</span>
                      )}
                    </div>
                    <div className="chips">
                      {group.items &&
                        group.items.map((sub, sIdx) => (
                          <OptionChip key={sub.optionItemId || sIdx}>
                            {sub.itemName || sub.name}
                            {sub.additionalPrice > 0 && (
                              <span className="price">
                                +{sub.additionalPrice.toLocaleString()}원
                              </span>
                            )}
                            {sub.default && (
                              <span className="default-tag">(기본값)</span>
                            )}
                          </OptionChip>
                        ))}
                    </div>
                  </OptionGroupCard>
                ))}
              </OptionSection>
            )}

          <FooterButton onClick={onClose}>닫기</FooterButton>
        </ScrollContent>
      </ModalContainer>
    </ModalOverlay>
  );
}

export default ProductDetailModal;
