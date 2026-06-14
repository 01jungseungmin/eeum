import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { Plus } from 'lucide-react';

import CategoryStats from '../../../components/owner/category/CategoryStats';
import CategoryRow from '../../../components/owner/category/CategoryRow';
import AddCategoryModal from '../../../components/owner/category/AddCategoryModal';
import CategoryInfo from '../../../components/owner/category/CategoryInfo';

import { categoryApi } from '../../../api/owner/categoryApi';

const PageContainer = styled.div`
  padding: 24px;
  background: #f8f9fa;
  min-height: 100vh;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
`;

const MainCard = styled.div`
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  border: 1px solid #eef0f2;
  margin-bottom: 24px;
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  .title-group {
    h2 {
      font-size: 15px;
      font-weight: bold;
      color: #1a1f2c;
      margin: 0 0 4px 0;
    }
    p {
      font-size: 12px;
      color: #8e94a0;
      margin: 0;
    }
  }
`;

const AddButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  background: #00a651;
  color: white;
  border: none;
  padding: 10px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: bold;
  cursor: pointer;
  &:hover {
    background: #008f45;
  }
`;

const Table = styled.div`
  width: 100%;
`;

const TableHeader = styled.div`
  display: grid;
  grid-template-columns: 80px 1fr 100px 120px 100px;
  padding: 12px 16px;
  border-bottom: 1px solid #eef0f2;
  font-size: 12px;
  color: #8e94a0;
  font-weight: 500;
  text-align: center;
  div:nth-child(2) {
    text-align: left;
    padding-left: 20px;
  }
`;

function CategoryPage() {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);

  // 서브 UI 제어용 상태 관리
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [editingName, setEditingName] = useState('');

  // 카테고리 목록 조회
  const fetchCategories = async () => {
    try {
      setLoading(true);
      const response = await categoryApi.getOwnerProductCategories();

      if (response.data && response.data.success) {
        const rawData = response.data.data || [];

        const formattedData = rawData
          .map((cat) => ({
            id: cat.productCategoryId,
            name: cat.name,
            productCount: cat.productCount || 0,
            isVisible: cat.active,
            displayOrder: cat.displayOrder,
          }))
          .sort((a, b) => a.displayOrder - b.displayOrder);

        setCategories(formattedData);
      } else {
        alert(response.data.message || '카테고리 목록을 가져오지 못했습니다.');
      }
    } catch (error) {
      console.error('카테고리 API 호출 오류:', error);
      alert('서버와 통신 중 에러가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCategories();
  }, []);

  // 카테고리 추가 생성 핸들러
  const handleAddCategory = async () => {
    if (!newCategoryName.trim()) return;

    try {
      const payload = {
        name: newCategoryName.trim(),
        displayOrder: categories.length,
      };

      const response = await categoryApi.createOwnerProductCategory(payload);

      if (response.data && response.data.success) {
        setIsAddModalOpen(false);
        setNewCategoryName('');

        await fetchCategories();
      } else {
        alert(response.data.message || '카테고리 생성에 실패했습니다.');
      }
    } catch (error) {
      console.error('카테고리 등록 오류:', error);
      alert('카테고리를 등록하는 중 오류가 발생했습니다.');
    }
  };

  // 인라인 이름 수정 핸들러
  const handleStartEdit = (item) => {
    setEditingId(item.id);
    setEditingName(item.name);
  };

  // 상품 카테고리 이름 수정
  const handleSaveEdit = async (id) => {
    if (!editingName.trim()) return;

    const targetCategory = categories.find((cat) => cat.id === id);
    if (!targetCategory) return;

    try {
      const payload = {
        name: editingName.trim(),
        displayOrder: targetCategory.displayOrder,
      };

      const response = await categoryApi.updateOwnerProductCategory(
        id,
        payload,
      );

      if (response.data && response.data.success) {
        setEditingId(null);
        await fetchCategories();
      } else {
        alert(response.data.message || '카테고리 수정에 실패했습니다.');
      }
    } catch (error) {
      console.error('카테고리 수정 오류:', error);
      alert('카테고리 이름을 수정하는 중 오류가 발생했습니다.');
    }
  };

  // 노출 / 숨김 상태 토글 핸들러
  const handleToggleVisibility = async (id) => {
    const targetCategory = categories.find((cat) => cat.id === id);
    if (!targetCategory) return;

    try {
      let response;
      // 현재 노출(isVisible: true) 상태면 비활성화(deactivate) API를, 반대면 활성화(activate) API 호출
      if (targetCategory.isVisible) {
        response = await categoryApi.deactivateOwnerProductCategory(id);
      } else {
        response = await categoryApi.activateOwnerProductCategory(id);
      }

      if (response.data && response.data.success) {
        await fetchCategories(); // 최신 상태 반영을 위해 목록 동기화
      } else {
        alert(response.data.message || '상태 변경에 실패했습니다.');
      }
    } catch (error) {
      console.error('카테고리 상태 토글 오류:', error);
      alert('상태를 변경하는 중 오류가 발생했습니다.');
    }
  };

  // 카테고리 삭제 요청 핸들러
  const handleDeleteCategory = async (id, name) => {
    if (window.confirm(`[${name}] 카테고리를 정말 삭제하시겠습니까?`)) {
      try {
        const response = await categoryApi.deleteOwnerProductCategory(id);

        if (response.data && response.data.success) {
          await fetchCategories(); // 최신 목록 새로고침
        } else {
          alert(response.data.message || '카테고리 삭제에 실패했습니다.');
        }
      } catch (error) {
        console.error('카테고리 삭제 오류:', error);
        alert('카테고리를 삭제하는 중 오류가 발생했습니다.');
      }
    }
  };

  // 순서 위/아래 이동 핸들러
  const handleMoveOrder = async (index, direction) => {
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= categories.length) return;

    const updated = [...categories];
    const temp = updated[index];
    updated[index] = updated[targetIndex];
    updated[targetIndex] = temp;

    try {
      const firstUpdate = categoryApi.updateOwnerProductCategory(
        updated[index].id,
        {
          name: updated[index].name,
          displayOrder: index,
        },
      );
      const secondUpdate = categoryApi.updateOwnerProductCategory(
        updated[targetIndex].id,
        {
          name: updated[targetIndex].name,
          displayOrder: targetIndex,
        },
      );

      await Promise.all([firstUpdate, secondUpdate]);
      await fetchCategories();
    } catch (error) {
      console.error('순서 변경 오류:', error);
      alert('순서를 서버에 저장하는 도중 오류가 발생했습니다.');
    }
  };

  // 통계 계산 파트
  const totalCount = categories.length;
  const activeCount = categories.filter((c) => c.isVisible).length;
  const totalProducts = categories.reduce((sum, c) => sum + c.productCount, 0);

  if (loading) {
    return (
      <PageContainer>
        <div
          style={{
            textAlign: 'center',
            padding: '80px 0',
            color: '#8e94a0',
            fontSize: '14px',
          }}
        >
          🔄 카테고리 목록을 불러오는 중입니다...
        </div>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <CategoryStats
        totalCount={totalCount}
        activeCount={activeCount}
        totalProducts={totalProducts}
      />

      <MainCard>
        <CardHeader>
          <div className="title-group">
            <h2>카테고리 관리</h2>
            <p>
              ↑↓ 버튼으로 카테고리 순서를 변경하세요. 앱에 이 순서대로
              표시됩니다.
            </p>
          </div>
          <AddButton
            onClick={() => {
              setNewCategoryName('');
              setIsAddModalOpen(true);
            }}
          >
            <Plus size={16} strokeWidth={2.5} /> 카테고리 추가
          </AddButton>
        </CardHeader>

        <Table>
          <TableHeader>
            <div>순서</div>
            <div>카테고리명</div>
            <div>상품수</div>
            <div>노출</div>
            <div>관리</div>
          </TableHeader>

          {categories.length === 0 ? (
            <div
              style={{
                textAlign: 'center',
                padding: '40px 0',
                color: '#cbd5e1',
                fontSize: '13px',
              }}
            >
              등록된 카테고리가 없습니다.
            </div>
          ) : (
            categories.map((cat, index) => (
              <CategoryRow
                key={cat.id}
                cat={cat}
                index={index}
                isFirst={index === 0}
                isLast={index === categories.length - 1}
                editingId={editingId}
                editingName={editingName}
                setEditingName={setEditingName}
                onStartEdit={handleStartEdit}
                onSaveEdit={handleSaveEdit}
                onCancelEdit={() => setEditingId(null)}
                onToggleVisibility={handleToggleVisibility}
                onDeleteCategory={handleDeleteCategory}
                onMoveOrder={handleMoveOrder}
              />
            ))
          )}
        </Table>
      </MainCard>

      <CategoryInfo />

      <AddCategoryModal
        isOpen={isAddModalOpen}
        onClose={() => setIsAddModalOpen(false)}
        onAdd={handleAddCategory}
        newCategoryName={newCategoryName}
        setNewCategoryName={setNewCategoryName}
      />
    </PageContainer>
  );
}

export default CategoryPage;
