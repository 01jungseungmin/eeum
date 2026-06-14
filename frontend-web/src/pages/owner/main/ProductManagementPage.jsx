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
  const [typeFilter, setTypeFilter] = useState('ALL'); // 전체유형/판매(SALE)/메뉴(MENU)

  const [selectedIds, setSelectedIds] = useState([]);
  const [isPostModalOpen, setIsPostModalOpen] = useState(false);
  const [selectedProductId, setSelectedProductId] = useState(null);
  const [editingProductId, setEditingProductId] = useState(null);

  const fetchProductsWithImages = async () => {
    try {
      setLoading(true);

      // 상품 기본 목록 조회
      const listResponse = await productApi.getOwnerProducts();

      if (listResponse.data && listResponse.data.success) {
        const productList = listResponse.data.data || [];

        // 각 상품의 대표 이미지 병렬 호출
        const imagePromises = productList.map(async (product) => {
          try {
            const imgResponse = await productApi.getProductMainImage(
              product.productId,
            );

            if (
              imgResponse.data &&
              imgResponse.data.success &&
              imgResponse.data.data
            ) {
              return {
                productId: product.productId,
                imageUrl: imgResponse.data.data.imageUrl,
              };
            }
          } catch (err) {
            if (err.response && err.response.status === 404) {
              return { productId: product.productId, imageUrl: null };
            }
            console.warn(
              `상품 ID ${product.productId} 이미지 로드 실패:`,
              err.message,
            );
          }
          return { productId: product.productId, imageUrl: null };
        });

        const imageResults = await Promise.all(imagePromises);

        // 상품 정보와 이미지 주소 조립 (Merge)
        const mergedProducts = productList.map((product) => {
          const matchImage = imageResults.find(
            (img) => img.productId === product.productId,
          );
          return {
            ...product,
            imageUrl: matchImage ? matchImage.imageUrl : null,
          };
        });

        setProducts(mergedProducts);
      }
    } catch (error) {
      console.error('상품 목록 및 이미지 전체 로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProductsWithImages();
  }, []);

  const activeProducts = products.filter((p) => p.status !== 'INACTIVE');

  // 상품 삭제
  const handleDeleteProduct = async (productId) => {
    const isConfirmed = window.confirm(
      '정말 이 상품을 삭제하시겠습니까?\n삭제된 상품은 매장 목록 및 판매 대상에서 제외됩니다.',
    );

    if (!isConfirmed) return; // 취소 누르면 중단

    try {
      const response = await productApi.deleteOwnerProduct(productId);
      if (response.data && response.data.success) {
        alert('상품이 성공적으로 삭제되었습니다.');
        fetchProductsWithImages();
      }
    } catch (error) {
      console.error('상품 삭제 오류:', error);
      alert('상품 삭제 중 오류가 발생했습니다. 다시 시도해 주세요.');
    }
  };

  // 상품 상태 변경
  const handleToggleStatus = async (productId, currentStatus) => {
    // INACTIVE(삭제됨) 상태인 상품은 목록 클릭으로 함부로 변경되지 않게 차단
    if (currentStatus === 'INACTIVE') {
      alert(
        '삭제(비공개)된 상품은 수정 모달이나 복구 절차를 통해 상태를 변경해 주세요.',
      );
      return;
    }

    // 현재 판매중이면 품절로, 품절이면 판매중으로 상태값 스위칭
    const nextStatus = currentStatus === 'ACTIVE' ? 'SOLD_OUT' : 'ACTIVE';
    const statusText = nextStatus === 'ACTIVE' ? '판매중' : '품절';

    const isConfirmed = window.confirm(
      `상품 상태를 [${statusText}]으로 변경하시겠습니까?`,
    );
    if (!isConfirmed) return;

    try {
      const response = await productApi.updateProductStatus(
        productId,
        nextStatus,
      );
      if (response.data && response.data.success) {
        alert(`성공적으로 ${statusText} 상태로 변경되었습니다.`);
        fetchProductsWithImages();
      }
    } catch (error) {
      console.error('상태 변경 실패:', error);
      alert('상태 변경 중 오류가 발생했습니다.');
    }
  };

  const filteredProducts = products.filter((product) => {
    // 검색어 필터 (상품명 / 카테고리 매칭)
    const matchesSearch =
      product.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (product.categoryName &&
        product.categoryName.toLowerCase().includes(searchTerm.toLowerCase()));

    // 상태 필터 및 Soft-Delete(INACTIVE) 처리
    let matchesStatus = true;

    if (statusFilter === 'ALL') {
      // 전체 탭일 때는 삭제(비공개)된 INACTIVE 상품을 철저히 숨깁니다.
      matchesStatus = product.status !== 'INACTIVE';
    } else if (statusFilter === 'INACTIVE') {
      // 탭을 직접 눌렀을 때만 INACTIVE 상품들을 걸러서 보여줍니다.
      matchesStatus = product.status === 'INACTIVE';
    } else {
      matchesStatus =
        product.status === statusFilter && product.status !== 'INACTIVE';
    }

    // 유형 필터 (전체유형 / SALE / MENU)
    let matchesType = true;
    if (typeFilter !== 'ALL') {
      matchesType = product.productType === typeFilter;
    }

    // 세 가지 필터 조건이 모두 참이어야 화면에 노출됨
    return matchesSearch && matchesStatus && matchesType;
  });

  // 일괄 상태 변경 처리
  const handleBulkStatusChange = async (targetStatus) => {
    if (selectedIds.length === 0) {
      alert('선택된 상품이 없습니다. 처리할 상품을 체크해 주세요.');
      return;
    }

    const statusText = targetStatus === 'ACTIVE' ? '판매 활성화' : '품절 처리';
    const isConfirmed = window.confirm(
      `선택한 ${selectedIds.length}개의 상품을 일괄 [${statusText}] 하시겠습니까?`,
    );
    if (!isConfirmed) return;

    try {
      await Promise.all(
        selectedIds.map((productId) =>
          productApi.updateProductStatus(productId, targetStatus),
        ),
      );

      alert(`선택한 상품들이 성공적으로 ${statusText} 되었습니다.`);
      setSelectedIds([]);
      fetchProductsWithImages();
    } catch (error) {
      console.error(`일괄 ${statusText} 중 오류 발생:`, error);
      alert(
        '일괄 처리 중 일부 상품에서 오류가 발생했습니다. 다시 시도해 주세요.',
      );
    }
  };

  // 일괄 삭제 처리
  const handleBulkDelete = async () => {
    if (selectedIds.length === 0) {
      alert('선택된 상품이 없습니다. 삭제할 상품을 체크해 주세요.');
      return;
    }

    const isConfirmed = window.confirm(
      `정말 선택한 ${selectedIds.length}개의 상품을 일괄 삭제하시겠습니까?\n삭제된 상품은 매장 목록에서 제외됩니다.`,
    );
    if (!isConfirmed) return;

    try {
      await Promise.all(
        selectedIds.map((productId) =>
          productApi.deleteOwnerProduct(productId),
        ),
      );

      alert('선택한 상품들이 성공적으로 삭제(비공개) 되었습니다.');
      setSelectedIds([]); // 체크박스 선택 초기화
      fetchProductsWithImages(); // 🔄 목록 및 상단 통계 새로고침
    } catch (error) {
      console.error('일괄 삭제 중 오류 발생:', error);
      alert('일괄 삭제 중 일부 상품에서 오류가 발생했습니다.');
    }
  };

  return (
    <PageContainer>
      <ProductStats products={activeProducts} />
      <ProductFilterBar
        searchTerm={searchTerm}
        onSearchChange={setSearchTerm}
        statusFilter={statusFilter}
        onStatusChange={setStatusFilter}
        typeFilter={typeFilter}
        onTypeChange={setTypeFilter}
        onOpenRegisterModal={() => setIsPostModalOpen(true)}
        selectedIds={selectedIds}
        onBulkStatusChange={handleBulkStatusChange}
        onBulkDelete={handleBulkDelete}
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
          onToggleStatus={handleToggleStatus}
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
          onSuccess={fetchProductsWithImages}
        />
      )}

      {/* 상품 수정 모달 */}
      {editingProductId !== null && (
        <ProductFormModal
          mode="EDIT"
          productId={editingProductId}
          onClose={() => setEditingProductId(null)}
          onSuccess={fetchProductsWithImages}
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
