import styled from 'styled-components';

const RightDetailPanel = styled.div`
  background: white;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  padding: 32px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 32px;

  h2 {
    margin: 0 0 20px 0;
    font-size: 16px;
    font-weight: 700;
    color: #262626;
  }

  .info-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 20px 40px;

    .field-block {
      .label {
        font-size: 12px;
        color: #8c8c8c;
        margin-bottom: 6px;
      }
      .value {
        font-size: 14px;
        color: #262626;
        font-weight: 500;
      }
    }
  }
`;

const Section = styled.div`
  &:not(:last-child) {
    padding-bottom: 32px;
    border-bottom: 1px solid #f0f0f0;
  }
`;

const ContentBox = styled.p`
  font-size: 14px;
  color: #262626;
  line-height: 1.7;
  white-space: pre-wrap;
  margin: 0;
`;

const ImageGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 12px;
`;

const ImageThumb = styled.img`
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
`;

const EmptyText = styled.div`
  font-size: 13px;
  color: #bfbfbf;
`;

const formatDate = (value) =>
  value ? new Date(value).toLocaleString('ko-KR') : '-';

function UsedProductDetailPanel({ product }) {
  return (
    <RightDetailPanel>
      <Section>
        <h2>게시글 정보</h2>
        <div className="info-grid">
          <div className="field-block">
            <div className="label">작성일시</div>
            <div className="value">{formatDate(product.createdAt)}</div>
          </div>
          <div className="field-block">
            <div className="label">최종 수정일시</div>
            <div className="value">{formatDate(product.modifiedAt)}</div>
          </div>
          <div className="field-block">
            <div className="label">거래 장소</div>
            <div className="value">{product.tradeLocationName || '-'}</div>
          </div>
          <div className="field-block">
            <div className="label">지정된 거래 상대</div>
            <div className="value">{product.buyerNickname || '-'}</div>
          </div>
        </div>
      </Section>

      <Section>
        <h2>본문</h2>
        <ContentBox>{product.content || '내용이 없어요.'}</ContentBox>
      </Section>

      <Section>
        <h2>사진</h2>
        {product.images?.length > 0 ? (
          <ImageGrid>
            {product.images.map((img) => (
              <ImageThumb
                key={img.imageId}
                src={img.imageUrl}
                alt={product.title}
              />
            ))}
          </ImageGrid>
        ) : (
          <EmptyText>등록된 사진이 없어요.</EmptyText>
        )}
      </Section>
    </RightDetailPanel>
  );
}

export default UsedProductDetailPanel;
