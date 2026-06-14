import React from 'react';
import styled from 'styled-components';
import {
  ChevronUp,
  ChevronDown,
  Tag,
  Check,
  X,
  Eye,
  EyeOff,
  Pencil,
  Trash2,
} from 'lucide-react';

const TableRow = styled.div`
  display: grid;
  grid-template-columns: 80px 1fr 100px 120px 100px;
  padding: 16px;
  border-bottom: 1px solid #eef0f2;
  align-items: center;
  text-align: center;
  font-size: 13px;

  &:last-child {
    border-bottom: none;
  }
`;

const OrderColumn = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #cbd5e1;

  .arrows {
    display: flex;
    flex-direction: column;
    button {
      background: none;
      border: none;
      padding: 0;
      cursor: pointer;
      color: #cbd5e1;
      height: 12px;
      display: flex;
      align-items: center;

      &:hover {
        color: #8e94a0;
      }
      &:disabled {
        color: #f1f3f5;
        cursor: not-allowed;
      }
    }
  }
  .num {
    color: #8e94a0;
    width: 14px;
    font-weight: 500;
  }
`;

const NameColumn = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  padding-left: 20px;
`;

const TagIconWrapper = styled.div`
  background: ${(props) => (props.$hidden ? '#f1f3f5' : '#e6f7ed')};
  color: ${(props) => (props.$hidden ? '#8e94a0' : '#00a651')};
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const CategoryNameText = styled.span`
  font-weight: 600;
  color: ${(props) => (props.$hidden ? '#8e94a0' : '#1a1f2c')};
`;

const InlineEditInputWrapper = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 320px;

  input {
    flex: 1;
    border: 1px solid #00a651;
    border-radius: 10px;
    padding: 8px 14px;
    font-size: 13px;
    outline: none;
    font-weight: 500;
  }

  button {
    background: none;
    border: none;
    cursor: pointer;
    padding: 4px;
    display: flex;
    align-items: center;
  }
  .save {
    color: #00a651;
  }
  .cancel {
    color: #8e94a0;
  }
`;

const CountBadge = styled.span`
  background: ${(props) => (props.$count > 0 ? '#e6f7ed' : 'transparent')};
  color: ${(props) => (props.$count > 0 ? '#00a651' : '#8e94a0')};
  padding: 4px 8px;
  border-radius: 6px;
  font-weight: bold;
  font-size: 12px;
  display: inline-block;
`;

const VisibilityButton = styled.button`
  background: none;
  border: none;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  font-weight: bold;
  padding: 6px 12px;
  margin: 0 auto;
  color: ${(props) => (props.$visible ? '#00a651' : '#8e94a0')};

  &:hover {
    opacity: 0.8;
  }
`;

const ActionGroup = styled.div`
  display: flex;
  justify-content: center;
  gap: 12px;

  button {
    background: none;
    border: none;
    cursor: pointer;
    padding: 4px;
    display: flex;
    align-items: center;
  }
  .edit {
    color: #00a651;
    &:hover {
      color: #008f45;
    }
  }
  .delete {
    color: #ff4d4d;
    &:hover {
      color: #e03b3b;
    }
  }
`;

function CategoryRow({
  cat,
  index,
  isFirst,
  isLast,
  editingId,
  editingName,
  setEditingName,
  onStartEdit,
  onSaveEdit,
  onCancelEdit,
  onToggleVisibility,
  onDeleteCategory,
  onMoveOrder,
}) {
  const isEditing = editingId === cat.id;

  return (
    <TableRow>
      {/* 순서 조정 */}
      <OrderColumn>
        <div className="arrows">
          <button onClick={() => onMoveOrder(index, 'up')} disabled={isFirst}>
            <ChevronUp size={14} strokeWidth={2.5} />
          </button>
          <button onClick={() => onMoveOrder(index, 'down')} disabled={isLast}>
            <ChevronDown size={14} strokeWidth={2.5} />
          </button>
        </div>
        <div className="num">{index + 1}</div>
      </OrderColumn>

      {/* 카테고리명 (일반 상태 vs 수정 입력 상태 분기) */}
      <NameColumn>
        {isEditing ? (
          <InlineEditInputWrapper onClick={(e) => e.stopPropagation()}>
            <input
              type="text"
              value={editingName}
              onChange={(e) => setEditingName(e.target.value)}
              autoFocus
            />
            <button className="save" onClick={() => onSaveEdit(cat.id)}>
              <Check size={16} strokeWidth={2.5} />
            </button>
            <button className="cancel" onClick={onCancelEdit}>
              <X size={16} strokeWidth={2.5} />
            </button>
          </InlineEditInputWrapper>
        ) : (
          <>
            <TagIconWrapper $hidden={!cat.isVisible}>
              <Tag size={14} fill={cat.isVisible ? '#00a651' : 'none'} />
            </TagIconWrapper>
            <CategoryNameText $hidden={!cat.isVisible}>
              {cat.name}
            </CategoryNameText>
          </>
        )}
      </NameColumn>

      {/* 등록 상품 수 */}
      <div>
        <CountBadge $count={cat.productCount}>{cat.productCount}개</CountBadge>
      </div>

      {/* 노출/숨김 상태 전환 */}
      <div>
        <VisibilityButton
          $visible={cat.isVisible}
          onClick={() => onToggleVisibility(cat.id)}
        >
          {cat.isVisible ? (
            <>
              <Eye size={14} strokeWidth={2.5} /> 노출중
            </>
          ) : (
            <>
              <EyeOff size={14} strokeWidth={2.5} /> 숨김
            </>
          )}
        </VisibilityButton>
      </div>

      {/* 관리 액션 아이콘 그룹 */}
      <ActionGroup>
        <button className="edit" onClick={() => onStartEdit(cat)}>
          <Pencil size={15} />
        </button>
        <button
          className="delete"
          onClick={() => onDeleteCategory(cat.id, cat.name)}
        >
          <Trash2 size={15} />
        </button>
      </ActionGroup>
    </TableRow>
  );
}

export default CategoryRow;
