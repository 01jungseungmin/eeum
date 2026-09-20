import { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { ArrowLeft } from 'lucide-react';
import UsedProductProfileCard from '../../../components/admin/used/UsedProductProfileCard';
import UsedProductDetailPanel from '../../../components/admin/used/UsedProductDetailPanel';
import { usedProductApi } from '../../../api/admin/usedProductApi';

const DetailContainer = styled.div`
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

const DetailHeader = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;

  .btn-back {
    background: white;
    border: 1px solid #d9d9d9;
    border-radius: 6px;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: #595959;
    transition: background 0.2s;
    &:hover {
      background: #f5f5f5;
    }
  }

  .title-side {
    h1 {
      margin: 0 0 6px 0;
      font-size: 22px;
      font-weight: 700;
      color: #262626;
    }
    p {
      margin: 0;
      font-size: 13px;
      color: #8c8c8c;
    }
  }
`;

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
  align-items: start;
`;

function UsedProductDetailPage() {
  const { usedProductId } = useParams();
  const navigate = useNavigate();

  const [product, setProduct] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  const fetchDetail = useCallback(async () => {
    try {
      setIsLoading(true);
      const res = await usedProductApi.getUsedProductDetail(usedProductId);
      if (res.data?.success) {
        setProduct(res.data.data);
      } else {
        alert('상세 정보를 불러올 수 없습니다.');
        navigate('/admin/used');
      }
    } catch (error) {
      console.error('중고거래 상세 조회 실패:', error);
      alert('데이터 로드 실패로 목록으로 이동합니다.');
      navigate('/admin/used');
    } finally {
      setIsLoading(false);
    }
  }, [usedProductId, navigate]);

  useEffect(() => {
    if (usedProductId) queueMicrotask(() => fetchDetail());
  }, [usedProductId, fetchDetail]);

  const handleHide = async () => {
    if (!window.confirm(`[${product.title}] 게시글을 숨기시겠습니까?`)) return;
    try {
      await usedProductApi.hideUsedProduct(usedProductId);
      alert('게시글이 숨김 처리되었습니다.');
      fetchDetail();
    } catch (error) {
      alert(
        error.response?.data?.error?.message ||
          '숨김 처리 중 오류가 발생했습니다.',
      );
    }
  };

  const handleShow = async () => {
    if (!window.confirm(`[${product.title}] 게시글의 숨김을 해제하시겠습니까?`))
      return;
    try {
      await usedProductApi.showUsedProduct(usedProductId);
      alert('게시글 숨김이 해제되었습니다.');
      fetchDetail();
    } catch (error) {
      alert(
        error.response?.data?.error?.message ||
          '숨김 해제 처리 중 오류가 발생했습니다.',
      );
    }
  };

  if (isLoading)
    return (
      <DetailContainer style={{ textAlign: 'center', padding: '100px' }}>
        데이터 조회 중...
      </DetailContainer>
    );
  if (!product) return null;

  return (
    <DetailContainer>
      <DetailHeader>
        <div
          className="btn-back"
          onClick={() => navigate('/admin/used')}
        >
          <ArrowLeft size={18} />
        </div>
        <div className="title-side">
          <h1>{product.title}</h1>
          <p>{product.categoryName}</p>
        </div>
      </DetailHeader>

      <MainGrid>
        <UsedProductProfileCard
          product={product}
          onHide={handleHide}
          onShow={handleShow}
        />
        <UsedProductDetailPanel product={product} />
      </MainGrid>
    </DetailContainer>
  );
}

export default UsedProductDetailPage;
