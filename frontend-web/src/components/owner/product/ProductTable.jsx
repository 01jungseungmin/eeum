import React from 'react';
import styled from 'styled-components';
import { Eye, Pencil, Trash2 } from 'lucide-react';

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

const StatusBadge = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: bold;

  ${(props) =>
    props.$status === 'INACTIVE' &&
    `
      background-color: #fff0f0;
      color: #ff4b4b;
    `}

  ${(props) =>
    props.$status === 'ACTIVE' &&
    `
      background-color: #e6f6ed; 
      color: #00a651;
    `}
`;

const ActionContainer = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
`;

const ActionButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  transition: all 0.15s ease-in-out;

  &.view {
    color: #4361ee;
    &:hover {
      background: #eff6ff;
      color: #1d4ed8;
    }
  }
  &.edit {
    color: #00a651;
    &:hover {
      background: #f4fbf7;
      color: #007336;
    }
  }
  &.delete {
    color: #ff4d4d;
    &:hover {
      background: #fff5f5;
      color: #c53030;
    }
  }
  &:active {
    transform: scale(0.92);
  }
`;

// 🎯 프롭스에 selectedIds와 setSelectedIds를 추가합니다.
function ProductTable({
  products = [],
  selectedIds = [],
  setSelectedIds,
  onView,
  onEdit,
  onDelete,
}) {
  const TYPE_MAP = {
    SALE: { text: '판매 상품', color: '판매 상품' },
    PREORDER: { text: '예약 상품', color: '예약 상품' },
    MENU: { text: '메뉴 상품', color: '메뉴 상품' },
  };

  const STATUS_MAP = {
    ACTIVE: { text: '판매중', color: '판매중' },
    SOLD_OUT: { text: '품절', color: '품절' },
    INACTIVE: { text: '비공개', color: '비공개' },
  };

  // 💡 [선택 로직] 현재 필터링되어 보여지는 상품들이 전부 선택되었는지 확인
  const isAllSelected =
    products.length > 0 &&
    products.every((item) => selectedIds.includes(item.productId));

  // 💡 [전체 선택 헤더 체크박스 핸들러]
  const handleSelectAll = () => {
    if (isAllSelected) {
      // 현재 리스트에 보이는 상품 ID들을 선택 목록에서 일괄 제거
      const currentIds = products.map((p) => p.productId);
      setSelectedIds((prev) => prev.filter((id) => !currentIds.includes(id)));
    } else {
      // 현재 리스트에 보이는 상품 ID들을 선택 목록에 일괄 추가 (중복 방지 세트화)
      const currentIds = products.map((p) => p.productId);
      setSelectedIds((prev) => Array.from(new Set([...prev, ...currentIds])));
    }
  };

  // 💡 [개별 행 체크박스 핸들러]
  const handleSelectRow = (productId) => {
    if (selectedIds.includes(productId)) {
      setSelectedIds((prev) => prev.filter((id) => id !== productId));
    } else {
      setSelectedIds((prev) => [...prev, productId]);
    }
  };

  return (
    <TableContainer>
      <StyledTable>
        <thead>
          <tr>
            <Th style={{ width: '40px' }}>
              {/* 🎯 상단 마스터 체크박스 바인딩 */}
              <input
                type="checkbox"
                checked={isAllSelected}
                onChange={handleSelectAll}
              />
            </Th>
            <Th>상품</Th>
            <Th>유형</Th>
            <Th>카테고리</Th>
            <Th>가격</Th>
            <Th>픽업</Th>
            <Th>재고</Th>
            <Th>상태</Th>
            <Th style={{ width: '120px' }}>관리</Th>
          </tr>
        </thead>
        <tbody>
          {products.map((item) => {
            const id = `P${String(item.productId).padStart(3, '0')}`;
            const typeInfo = TYPE_MAP[item.productType] || {
              text: item.productType,
              color: '메뉴 상품',
            };
            const statusInfo = STATUS_MAP[item.status] || {
              text: item.status,
              color: '비공개',
            };
            const pickupTime = item.pickupTime || item.pickup;

            return (
              <tr key={item.productId}>
                <Td>
                  {/* 🎯 개별 행 체크박스 바인딩 */}
                  <input
                    type="checkbox"
                    checked={selectedIds.includes(item.productId)}
                    onChange={() => handleSelectRow(item.productId)}
                  />
                </Td>
                <Td>
                  <ProductInfo>
                    <img
                      src={item.imageUrl || 'https://via.placeholder.com/40'}
                      alt={item.name}
                    />
                    <div className="details">
                      <span className="name">{item.name}</span>
                      <span className="code">{id}</span>
                    </div>
                  </ProductInfo>
                </Td>
                <Td>
                  <TypeBadge $type={typeInfo.color}>{typeInfo.text}</TypeBadge>
                </Td>
                <Td style={{ color: '#666' }}>{item.categoryName}</Td>
                <Td style={{ fontWeight: 'bold' }}>
                  {item.productType === 'MENU'
                    ? '조회만'
                    : item.price === 0 && item.productType === 'PREORDER'
                      ? '무료예약'
                      : `${item.price.toLocaleString()}원`}
                </Td>
                <Td
                  style={{
                    color: pickupTime ? '#00a651' : '#999',
                    fontWeight: pickupTime ? '500' : 'normal',
                  }}
                >
                  {pickupTime ? `🕒 ${pickupTime}` : '—'}
                </Td>
                <Td
                  style={{
                    color: item.stock === 0 ? '#ff4d4d' : '#333',
                    fontWeight: item.stock === 0 ? 'bold' : 'normal',
                  }}
                >
                  {item.productType === 'MENU'
                    ? '미설정'
                    : item.stock === 0
                      ? '⚠️ 품절'
                      : `${item.stock}개`}
                </Td>
                <Td>
                  {item.status === 'INACTIVE' ? (
                    <StatusBadge $status="INACTIVE">품절</StatusBadge>
                  ) : (
                    <StatusBadge $status="ACTIVE">판매중</StatusBadge>
                  )}
                </Td>
                <Td>
                  <ActionContainer>
                    <ActionButton
                      className="view"
                      title="상세보기"
                      onClick={() => onView(item.productId)}
                    >
                      <Eye size={15} strokeWidth={2.3} />
                    </ActionButton>
                    <ActionButton
                      className="edit"
                      title="수정하기"
                      onClick={() => onEdit(item.productId)}
                    >
                      <Pencil size={14} strokeWidth={2.3} />
                    </ActionButton>
                    <ActionButton
                      className="delete"
                      title="삭제하기"
                      onClick={() => onDelete(item.productId)}
                    >
                      <Trash2 size={15} strokeWidth={2.3} />
                    </ActionButton>
                  </ActionContainer>
                </Td>
              </tr>
            );
          })}
        </tbody>
      </StyledTable>
    </TableContainer>
  );
}

export default ProductTable;
