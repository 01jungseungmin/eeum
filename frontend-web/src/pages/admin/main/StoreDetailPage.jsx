import { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { ArrowLeft, ShieldAlert } from 'lucide-react';
import StoreProfileCard from '../../../components/admin/store/StoreProfileCard';
import StoreDetailPanel from '../../../components/admin/store/StoreDetailPanel';
import SanctionHistoryModal from '../../../components/admin/sanction/SanctionHistoryModal';
import { storeApi } from '../../../api/admin/storeApi';

const DetailContainer = styled.div`
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

const DetailHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
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
      font-size: 24px;
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

const HeaderLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
`;

const SanctionHistoryButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: white;
  color: #374151;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    background: #f9fafb;
  }
`;

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
  align-items: start;
`;

function StoreDetailPage() {
  const { storeId } = useParams();
  const navigate = useNavigate();

  const [store, setStore] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [showSanctionHistory, setShowSanctionHistory] = useState(false);

  const fetchDetail = useCallback(async () => {
    try {
      setIsLoading(true);
      const res = await storeApi.getStoreDetail(storeId);
      if (res.data?.success) {
        setStore(res.data.data);
      } else {
        alert('상세 정보를 불러올 수 없습니다.');
        navigate('/admin/stores');
      }
    } catch (error) {
      console.error('상점 상세 조회 실패:', error);
      alert('데이터 로드 실패로 목록으로 이동합니다.');
      navigate('/admin/stores');
    } finally {
      setIsLoading(false);
    }
  }, [storeId, navigate]);

  useEffect(() => {
    if (storeId) queueMicrotask(() => fetchDetail());
  }, [storeId, fetchDetail]);

  const handleSuspend = async () => {
    if (!window.confirm(`[${store.name}] 상점을 정지하시겠습니까?`)) return;
    try {
      await storeApi.suspendStore(storeId);
      alert('상점이 정지 처리되었습니다.');
      fetchDetail();
    } catch (error) {
      console.error('상점 정지 실패:', error);
      alert(
        error.response?.data?.error?.message ||
          '정지 처리 중 오류가 발생했습니다.',
      );
    }
  };

  const handleActivate = async () => {
    if (!window.confirm(`[${store.name}] 상점의 정지를 해제하시겠습니까?`))
      return;
    try {
      await storeApi.activateStore(storeId);
      alert('상점 정지가 해제되었습니다.');
      fetchDetail();
    } catch (error) {
      console.error('상점 정지 해제 실패:', error);
      alert(
        error.response?.data?.error?.message ||
          '정지 해제 처리 중 오류가 발생했습니다.',
      );
    }
  };

  if (isLoading)
    return (
      <DetailContainer style={{ textAlign: 'center', padding: '100px' }}>
        데이터 조회 중...
      </DetailContainer>
    );
  if (!store) return null;

  return (
    <DetailContainer>
      <DetailHeader>
        <HeaderLeft>
          <div
            className="btn-back"
            onClick={() => navigate('/admin/stores')}
          >
            <ArrowLeft size={18} />
          </div>
          <div className="title-side">
            <h1>{store.name}</h1>
            <p>{store.address}</p>
          </div>
        </HeaderLeft>

        <SanctionHistoryButton onClick={() => setShowSanctionHistory(true)}>
          <ShieldAlert size={14} />
          제재 이력
        </SanctionHistoryButton>
      </DetailHeader>

      <MainGrid>
        <StoreProfileCard
          store={store}
          onSuspend={handleSuspend}
          onActivate={handleActivate}
        />
        <StoreDetailPanel store={store} />
      </MainGrid>

      {showSanctionHistory && (
        <SanctionHistoryModal
          targetType="STORE"
          targetId={storeId}
          targetLabel={`${store.name} (상점 #${storeId})`}
          onClose={() => setShowSanctionHistory(false)}
        />
      )}
    </DetailContainer>
  );
}

export default StoreDetailPage;
