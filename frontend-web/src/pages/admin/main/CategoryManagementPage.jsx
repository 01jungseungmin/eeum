import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import CategoryCard from '../../../components/admin/category/CategoryCard';
import AutoFilterSection from '../../../components/admin/category/AutoFilterSection';
import CategoryModal from '../../../components/admin/category/CategoryModal';
import { adminCategoryApi } from '../../../api/admin/CategoryApi';

const Container = styled.div`
  width: 100%;
  padding: 32px;
  background-color: #f8fafc;
  min-height: 100vh;
  box-sizing: border-box;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
`;

const CardGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin-bottom: 24px;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
`;

const initialReportCategories = [
  { id: 1, name: '사기/허위정보', count: '283건', active: true, isHot: true },
  { id: 2, name: '욕설/비방', count: '432건', active: true },
  { id: 3, name: '스팸/광고', count: '1,063건', active: true },
];

export default function CategoryManagementPage() {
  const [storeCategories, setStoreCategories] = useState([]);
  const [boardCategories, setBoardCategories] = useState([]);
  const [marketCategories, setMarketCategories] = useState([]);
  const [reportCategories, setReportCategories] = useState(
    initialReportCategories,
  );

  const [modalState, setModalState] = useState({
    isOpen: false,
    mode: 'ADD',
    type: null,
    targetCategory: null,
  });

  const mapCategoryData = (dataList = []) => {
    return dataList.map((item) => ({
      id: item.categoryId,
      name: item.name,
      type: item.type,
      parentId: item.parentId,
      displayOrder: item.displayOrder,
      count: '-',
      active: item.active,
    }));
  };

  const fetchAllCategories = async () => {
    try {
      const [storeRes, boardRes, marketRes] = await Promise.all([
        adminCategoryApi.getCategories('STORE'),
        adminCategoryApi.getCategories('COMMUNITY'),
        adminCategoryApi.getCategories('USED'),
      ]);

      if (storeRes.data?.success)
        setStoreCategories(mapCategoryData(storeRes.data.data));
      if (boardRes.data?.success)
        setBoardCategories(mapCategoryData(boardRes.data.data));
      if (marketRes.data?.success)
        setMarketCategories(mapCategoryData(marketRes.data.data));
    } catch (error) {
      console.error('카테고리 목록 불러오기 실패:', error);
    }
  };

  useEffect(() => {
    fetchAllCategories();
  }, []);

  // 📌 드래그 앤 드롭 순서 변경 핸들러
  const handleReorder = async (type, fromIndex, toIndex) => {
    let targetList = [];
    if (type === 'STORE') targetList = [...storeCategories];
    if (type === 'COMMUNITY') targetList = [...boardCategories];
    if (type === 'USED') targetList = [...marketCategories];

    if (fromIndex === toIndex || toIndex < 0 || toIndex >= targetList.length)
      return;

    // 리스트 위치 이동
    const updatedList = [...targetList];
    const [movedItem] = updatedList.splice(fromIndex, 1);
    updatedList.splice(toIndex, 0, movedItem);

    // 변경된 순서의 ID 배열 생성
    const categoryIds = updatedList.map((item) => item.id);

    const payload = {
      type: type,
      parentId: null,
      categoryIds: categoryIds,
    };

    try {
      const res = await adminCategoryApi.updateCategoryOrder(payload);
      if (res.data?.success) {
        fetchAllCategories();
      }
    } catch (error) {
      console.error('순서 변경 실패:', error);
      alert('순서 변경 실패했습니다.');
    }
  };

  const handleOpenAddModal = (type) => {
    setModalState({ isOpen: true, mode: 'ADD', type, targetCategory: null });
  };

  const handleOpenEditModal = (item) => {
    setModalState({
      isOpen: true,
      mode: 'EDIT',
      type: null,
      targetCategory: item,
    });
  };

  const handleCloseModal = () => {
    setModalState({
      isOpen: false,
      mode: 'ADD',
      type: null,
      targetCategory: null,
    });
  };

  const handleModalSubmit = async (name) => {
    try {
      if (modalState.mode === 'ADD') {
        let currentLength = 0;
        if (modalState.type === 'STORE') currentLength = storeCategories.length;
        if (modalState.type === 'COMMUNITY')
          currentLength = boardCategories.length;
        if (modalState.type === 'USED') currentLength = marketCategories.length;

        const payload = {
          type: modalState.type,
          parentId: null,
          name: name,
          displayOrder: currentLength,
        };

        const res = await adminCategoryApi.createCategory(payload);
        if (res.data?.success) fetchAllCategories();
      } else if (modalState.mode === 'EDIT') {
        const target = modalState.targetCategory;
        const payload = {
          type: target.type,
          parentId: target.parentId || null,
          name: name,
          displayOrder: target.displayOrder || 0,
        };

        const res = await adminCategoryApi.updateCategory(target.id, payload);
        if (res.data?.success) fetchAllCategories();
      }
      handleCloseModal();
    } catch (error) {
      console.error('카테고리 저장 실패:', error);
      alert(
        error.response?.data?.message || '요청 처리 중 오류가 발생했습니다.',
      );
    }
  };

  const handleToggle = async (categoryId, currentActive) => {
    try {
      if (currentActive) {
        await adminCategoryApi.deactivateCategory(categoryId);
      } else {
        await adminCategoryApi.activateCategory(categoryId);
      }
      fetchAllCategories();
    } catch (error) {
      console.error('상태 변경 실패:', error);
    }
  };

  const handleDelete = async (categoryId) => {
    if (!window.confirm('해당 카테고리를 삭제하시겠습니까?')) return;
    try {
      await adminCategoryApi.deleteCategory(categoryId);
      fetchAllCategories();
    } catch (error) {
      console.error('카테고리 삭제 실패:', error);
    }
  };

  return (
    <Container>
      <CardGrid>
        {/* 1. 가게 카테고리 */}
        <CategoryCard
          title="가게 카테고리"
          items={storeCategories}
          onToggle={(id) => {
            const item = storeCategories.find((c) => c.id === id);
            if (item) handleToggle(id, item.active);
          }}
          onEdit={handleOpenEditModal}
          onDelete={handleDelete}
          onAdd={() => handleOpenAddModal('STORE')}
          onReorder={(fromIdx, toIdx) => handleReorder('STORE', fromIdx, toIdx)}
        />

        {/* 2. 커뮤니티 게시판 */}
        <CategoryCard
          title="커뮤니티 게시판"
          items={boardCategories}
          onToggle={(id) => {
            const item = boardCategories.find((c) => c.id === id);
            if (item) handleToggle(id, item.active);
          }}
          onEdit={handleOpenEditModal}
          onDelete={handleDelete}
          onAdd={() => handleOpenAddModal('COMMUNITY')}
          onReorder={(fromIdx, toIdx) =>
            handleReorder('COMMUNITY', fromIdx, toIdx)
          }
        />

        {/* 3. 중고거래 카테고리 */}
        <CategoryCard
          title="중고거래 카테고리"
          items={marketCategories}
          onToggle={(id) => {
            const item = marketCategories.find((c) => c.id === id);
            if (item) handleToggle(id, item.active);
          }}
          onEdit={handleOpenEditModal}
          onDelete={handleDelete}
          onAdd={() => handleOpenAddModal('USED')}
          onReorder={(fromIdx, toIdx) => handleReorder('USED', fromIdx, toIdx)}
        />

        {/* 4. 신고 사유 */}
        <CategoryCard
          title="신고 사유"
          items={reportCategories}
          onToggle={(id) => {
            setReportCategories((prev) =>
              prev.map((item) =>
                item.id === id ? { ...item, active: !item.active } : item,
              ),
            );
          }}
        />
      </CardGrid>

      <AutoFilterSection />

      <CategoryModal
        isOpen={modalState.isOpen}
        mode={modalState.mode}
        initialValue={
          modalState.targetCategory ? modalState.targetCategory.name : ''
        }
        onClose={handleCloseModal}
        onSubmit={handleModalSubmit}
      />
    </Container>
  );
}
