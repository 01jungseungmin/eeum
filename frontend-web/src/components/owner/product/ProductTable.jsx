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
  gap: 14px; /* 피그마 비율에 맞춰 여백 살짝 조정 */

  /* 🖼️ 이미지와 대체 아이콘을 감싸는 프레임 박스 */
  .img-box {
    width: 44px; /* 이미지 크기 조절 */
    height: 44px;
    border-radius: 12px; /* 피그마 스타일의 부드러운 곡률 */
    background-color: #f1f5f9;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    flex-shrink: 0;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    /* 🛒 [image_bd97bf.png] 이미지가 없을 때 나타날 민트색 상점 템플릿 */
    .fallback-store {
      width: 100%;
      height: 100%;
      background-color: #e6f4ea; /* 연한 민트/그린 배경색 */
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }

  .details {
    display: flex;
    flex-direction: column;
    text-align: left;

    .name {
      font-weight: bold;
      color: #1a1f2c;
      margin-bottom: 2px;
      font-size: 14px;
    }
    .code {
      font-size: 11px;
      color: #8e94a0;
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
  padding: 5px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: bold;

  /* 💡 상태(프롭스)에 따른 배경색 및 글자색 동적 변경 */
  background-color: ${(props) => {
    if (props.$status === 'ACTIVE') return '#e6f4ea'; // 연한 초록
    if (props.$status === 'SOLD_OUT') return '#fce8e6'; // 연한 빨강
    return '#f1f3f5'; // INACTIVE (연한 회색)
  }};

  color: ${(props) => {
    if (props.$status === 'ACTIVE') return '#10b981'; // 초록 글씨
    if (props.$status === 'SOLD_OUT') return '#d93025'; // 빨강 글씨
    return '#666666'; // INACTIVE (회색 글씨)
  }};
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

function ProductTable({
  products = [],
  selectedIds = [],
  setSelectedIds,
  onToggleStatus,
  onView,
  onEdit,
  onDelete,
}) {
  const TYPE_MAP = {
    SALE: { text: '판매 상품', color: '판매 상품' },
    MENU: { text: '메뉴 상품', color: '메뉴 상품' },
  };

  const STATUS_MAP = {
    ACTIVE: { text: '판매중', color: '판매중' },
    SOLD_OUT: { text: '품절', color: '품절' },
    INACTIVE: { text: '비공개', color: '비공개' },
  };

  // 현재 필터링되어 보여지는 상품들이 전부 선택되었는지 확인
  const isAllSelected =
    products.length > 0 &&
    products.every((item) => selectedIds.includes(item.productId));

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
              <input
                type="checkbox"
                checked={isAllSelected}
                onChange={handleSelectAll}
              />
            </Th>
            <Th>상품명</Th>
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
                  <input
                    type="checkbox"
                    checked={selectedIds.includes(item.productId)}
                    onChange={() => handleSelectRow(item.productId)}
                  />
                </Td>
                <Td>
                  <ProductInfo>
                    <div className="img-box">
                      {item.imageUrl && item.imageUrl.trim() !== '' ? (
                        <img
                          src={item.imageUrl}
                          alt={item.name}
                          onError={(e) => {
                            // 무한 루프 방지 및 엑박 발생 시 즉시 상점 아이콘 프레임으로 교체
                            e.target.onerror = null;
                            const parent = e.target.parentNode;
                            if (parent) {
                              parent.innerHTML = `
                <div style="width: 100%; height: 100%; background-color: #e6f4ea; display: flex; align-items: center; justify-content: center;">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#10b981" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path><polyline points="9 22 9 12 15 12 15 22"></polyline></svg>
                </div>
              `;
                            }
                          }}
                        />
                      ) : (
                        /* 대표 이미지가 등록되지 않았을 때 뜨는 상점 아이콘 컴포넌트 */
                        <div className="fallback-store">
                          <svg
                            width="20"
                            height="20"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="#10b981"
                            strokeWidth="2"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          >
                            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                            <polyline points="9 22 9 12 15 12 15 22" />
                          </svg>
                        </div>
                      )}
                    </div>

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
                  {`${item.price.toLocaleString()}원`}
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
                  <div
                    onClick={() => onToggleStatus(item.productId, item.status)}
                    style={{
                      cursor:
                        item.status !== 'INACTIVE' ? 'pointer' : 'default',
                    }}
                  >
                    {(() => {
                      switch (item.status) {
                        case 'ACTIVE':
                          return (
                            <StatusBadge $status="ACTIVE">판매중</StatusBadge>
                          );
                        case 'SOLD_OUT':
                          return (
                            <StatusBadge $status="SOLD_OUT">품절</StatusBadge>
                          );
                        case 'INACTIVE':
                          return (
                            <StatusBadge $status="INACTIVE">삭제됨</StatusBadge>
                          );
                        default:
                          return (
                            <StatusBadge $status="DEFAULT">
                              {item.status}
                            </StatusBadge>
                          );
                      }
                    })()}
                  </div>
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
