// 📄 src/pages/owner/product/components/ProductTable.jsx
import styled from 'styled-components';

const TableContainer = styled.div`
  background: white;
  border-radius: 12px;
  border: 1px solid #eef0f2;
  overflow: hidden;
`;

const StyledTable = styled.table`
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
`;

const Th = styled.th`
  background: #f8f9fa;
  padding: 15px;
  color: #666;
  font-weight: 600;
  border-bottom: 1px solid #e9ecef;
`;

const Td = styled.td`
  padding: 15px;
  border-bottom: 1px solid #f1f3f5;
  vertical-align: middle;
`;

const ProductInfo = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;

  img {
    width: 42px;
    height: 42px;
    border-radius: 8px;
    object-fit: cover;
    border: 1px solid #eee;
  }
  .details {
    display: flex;
    flex-direction: column;
    text-align: left;
    .name {
      font-weight: bold;
      color: #333;
      margin-bottom: 2px;
    }
    .code {
      font-size: 11px;
      color: #999;
    }
  }
`;

// 유형별 배지 컬러 세팅
const TypeBadge = styled.span`
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: bold;
  background: ${(props) =>
    props.$type === '판매 상품'
      ? '#eef2ff'
      : props.$type === '예약 상품'
        ? '#fff9db'
        : '#f1f3f5'};
  color: ${(props) =>
    props.$type === '판매 상품'
      ? '#4361ee'
      : props.$type === '예약 상품'
        ? '#f7a110'
        : '#666'};
`;

// 상태 배지 (판매중/품절)
const StatusBadge = styled.span`
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  background: ${(props) =>
    props.$status === '판매중' ? '#e6f7ed' : '#ffebee'};
  color: ${(props) => (props.$status === '판매중' ? '#00a651' : '#ff4d4d')};
`;

const ActionGroup = styled.div`
  display: flex;
  gap: 12px;
  font-size: 16px;
  cursor: pointer;
  .view {
    color: #4361ee;
  }
  .edit {
    color: #00a651;
  }
  .delete {
    color: #ff4d4d;
  }
`;

function ProductTable({ products }) {
  return (
    <TableContainer>
      <StyledTable>
        <thead>
          <tr>
            <Th style={{ width: '40px' }}>
              <input type="checkbox" />
            </Th>
            <Th>상품</Th>
            <Th>유형</Th>
            <Th>카테고리</Th>
            <Th>가격</Th>
            <Th>픽업</Th>
            <Th>재고</Th>
            <Th>상태</Th>
            <Th style={{ width: '100px' }}>관리</Th>
          </tr>
        </thead>
        <tbody>
          {products.map((item) => (
            <tr key={item.id}>
              <Td>
                <input type="checkbox" />
              </Td>
              <Td>
                <ProductInfo>
                  <img src={item.img} alt={item.name} />
                  <div className="details">
                    <span className="name">{item.name}</span>
                    <span className="code">{item.id}</span>
                  </div>
                </ProductInfo>
              </Td>
              <Td>
                <TypeBadge $type={item.type}>{item.type}</TypeBadge>
              </Td>
              <Td style={{ color: '#666' }}>{item.category}</Td>
              <Td style={{ fontWeight: 'bold' }}>
                {typeof item.price === 'number'
                  ? `${item.price.toLocaleString()}원`
                  : item.price}
              </Td>
              <Td
                style={{
                  color: item.pickup !== '—' ? '#00a651' : '#999',
                  fontWeight: item.pickup !== '—' ? '500' : 'normal',
                }}
              >
                {item.pickup !== '—' && '🕒 '} {item.pickup}
              </Td>
              <Td
                style={{
                  color: item.stock === 0 ? '#ff4d4d' : '#333',
                  fontWeight: item.stock === 0 ? 'bold' : 'normal',
                }}
              >
                {item.stock === 0
                  ? '⚠️ 품절'
                  : typeof item.stock === 'number'
                    ? `${item.stock}개`
                    : item.stock}
              </Td>
              <Td>
                <StatusBadge $status={item.status}>{item.status}</StatusBadge>
              </Td>
              <Td>
                <ActionGroup>
                  <span className="view" title="상세보기">
                    👁️
                  </span>
                  <span className="edit" title="수정">
                    ✏️
                  </span>
                  <span className="delete" title="삭제">
                    🗑️
                  </span>
                </ActionGroup>
              </Td>
            </tr>
          ))}
        </tbody>
      </StyledTable>
    </TableContainer>
  );
}

export default ProductTable;
