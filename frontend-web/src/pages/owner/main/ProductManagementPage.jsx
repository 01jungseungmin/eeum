import styled from 'styled-components';
import ProductHeader from '../../../components/owner/product/ProductHeader';
import ProductStats from '../../../components/owner/product/ProductStats';
import ProductFilterBar from '../../../components/owner/product/ProductFilterBar';
import ProductTable from '../../../components/owner/product/ProductTable';

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
  // 나중에 API로 받아올 모크 데이터 세팅해두기
  const mockProducts = [
    {
      id: 'P001',
      name: '김치찌개 반찬 세트 (300g)',
      type: '판매 상품',
      category: '국/찌개',
      price: 9000,
      pickup: '10:00~19:00',
      stock: 15,
      status: '판매중',
      img: 'https://via.placeholder.com/40',
    },
    {
      id: 'P002',
      name: '된장찌개 반찬 (250g)',
      type: '판매 상품',
      category: '국/찌개',
      price: 8000,
      pickup: '11:00~18:00',
      stock: 8,
      status: '판매중',
      img: 'https://via.placeholder.com/40',
    },
    {
      id: 'P003',
      name: '불고기 반찬 (300g)',
      type: '판매 상품',
      category: '반찬류',
      price: 12000,
      pickup: '—',
      stock: 0,
      status: '품절',
      img: 'https://via.placeholder.com/40',
    },
    {
      id: 'P004',
      name: '방문 예약 - 반찬 세트',
      type: '예약 상품',
      category: '예약',
      price: 0,
      pickup: '09:00~17:00',
      stock: 10,
      status: '판매중',
      img: 'https://via.placeholder.com/40',
    },
    {
      id: 'P005',
      name: '계란말이 (1팩)',
      type: '판매 상품',
      category: '반찬류',
      price: 4500,
      pickup: '—',
      stock: 20,
      status: '판매중',
      img: 'https://via.placeholder.com/40',
    },
    {
      id: 'P006',
      name: '오늘의 메뉴판',
      type: '메뉴 상품',
      category: '메뉴',
      price: '조회만',
      pickup: '—',
      stock: '미설정',
      status: '판매중',
      img: 'https://via.placeholder.com/40',
    },
  ];

  return (
    <PageContainer>
      {/* 1. 상단 헤더 영역 */}
      <ProductHeader />

      {/* 2. 대시보드 통계 및 배너 영역 */}
      <ProductStats />

      {/* 3. 검색 및 필터 컨트롤러 영역 */}
      <ProductFilterBar />

      {/* 4. 상품 리스트 테이블 영역 */}
      <ProductTable products={mockProducts} />
    </PageContainer>
  );
}

export default ProductManagementPage;
