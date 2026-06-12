import { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { productApi } from '../../../api/owner/productApi';

import ProductStats from '../../../components/owner/product/ProductStats';
import ProductFilterBar from '../../../components/owner/product/ProductFilterBar';
import ProductTable from '../../../components/owner/product/ProductTable';
import ProductFormModal from '../../../components/owner/product/ProductFormModal';
import ProductDetailModal from '../../../components/owner/product/ProductDetailModal';

const PageContainer = styled.div`
  flex: 1;
  padding: 30px;
  background-color: #f8f9fa;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 25px;
`;

function ProductManagementPage() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);

  const [searchTerm, setSearchTerm] = useState(''); // 검색창 상태
  const [statusFilter, setStatusFilter] = useState('ALL'); // 전체/판매중(ACTIVE)/품절(INACTIVE)/비공개
  const [typeFilter, setTypeFilter] = useState('ALL'); // 전체유형/판매(SALE)/예약(PREORDER)/메뉴(MENU)

  const [selectedIds, setSelectedIds] = useState([]);
  const [isPostModalOpen, setIsPostModalOpen] = useState(false);
  const [selectedProductId, setSelectedProductId] = useState(null);
  const [editingProductId, setEditingProductId] = useState(null);

  const fetchProducts = async () => {
    try {
      setLoading(true);
      const response = await productApi.getOwnerProducts();
      if (response.data && response.data.success) {
        setProducts(response.data.data);
      }
    } catch (error) {
      console.error('목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProducts();
  }, []);

  const handleDeleteProduct = async (productId) => {
    const isConfirmed = window.confirm(
      '정말 이 상품을 삭제하시겠습니까?\n삭제된 상품은 매장 목록 및 판매 대상에서 제외됩니다.',
    );

    if (!isConfirmed) return; // 취소 누르면 중단

    try {
      const response = await productApi.deleteOwnerProduct(productId);
      if (response.data && response.data.success) {
        alert('상품이 성공적으로 삭제되었습니다.');
        fetchProducts();
      }
    } catch (error) {
      console.error('상품 삭제 오류:', error);
      alert('상품 삭제 중 오류가 발생했습니다. 다시 시도해 주세요.');
    }
  };

  const filteredProducts = products.filter((product) => {
    // 검색어 필터 (상품명 매칭)
    const matchesSearch = product.name
      .toLowerCase()
      .includes(searchTerm.toLowerCase());

    // 상태 필터 (전체 / ACTIVE / INACTIVE / HIDDEN)
    let matchesStatus = true;
    if (statusFilter !== 'ALL') {
      matchesStatus = product.status === statusFilter;
    }

    // 유형 필터 (전체유형 / SALE / PREORDER / MENU)
    let matchesType = true;
    if (typeFilter !== 'ALL') {
      matchesType = product.productType === typeFilter;
    }

    // 세 가지 필터 조건이 모두 참이어야 화면에 노출됨
    return matchesSearch && matchesStatus && matchesType;
  });
  return (
    <PageContainer>
      <ProductStats products={products} />
      <ProductFilterBar
        searchTerm={searchTerm}
        onSearchChange={setSearchTerm}
        statusFilter={statusFilter}
        onStatusChange={setStatusFilter}
        typeFilter={typeFilter}
        onTypeChange={setTypeFilter}
        onOpenRegisterModal={() => setIsPostModalOpen(true)}
      />
      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px', color: '#999' }}>
          데이터 로딩 중...
        </div>
      ) : (
        <ProductTable
          products={filteredProducts}
          selectedIds={selectedIds}
          setSelectedIds={setSelectedIds}
          onView={(id) => setSelectedProductId(id)}
          onEdit={(id) => setEditingProductId(id)}
          onDelete={handleDeleteProduct}
        />
      )}

      {/* 상품 등록 모달 */}
      {isPostModalOpen && (
        <ProductFormModal
          mode="CREATE"
          onClose={() => setIsPostModalOpen(false)}
          onSuccess={fetchProducts}
        />
      )}

      {/* 상품 수정 모달 */}
      {editingProductId !== null && (
        <ProductFormModal
          mode="EDIT"
          productId={editingProductId}
          onClose={() => setEditingProductId(null)}
          onSuccess={fetchProducts}
        />
      )}

      {/* 상품 상세 보기 모달 */}
      {selectedProductId !== null && (
        <ProductDetailModal
          productId={selectedProductId}
          onClose={() => setSelectedProductId(null)}
        />
      )}
    </PageContainer>
  );
}

export default ProductManagementPage;
