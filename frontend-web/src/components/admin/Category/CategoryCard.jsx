import React, { useState } from 'react';
import styled from 'styled-components';
import { Plus, SquarePen, Trash2, GripVertical } from 'lucide-react';

const Card = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #f1f5f9;
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
`;

const CardTitle = styled.h2`
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
`;

const AddButton = styled.button`
  display: flex;
  align-items: center;
  gap: 4px;
  background-color: #0f172a;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #1e293b;
  }
`;

const ItemList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const ItemRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #ffffff;
  border: 1px solid
    ${({ $isSelected }) => ($isSelected ? '#00b074' : '#f1f5f9')};
  border-radius: 12px;
  transition: all 0.2s ease;
  opacity: ${({ $isDragging }) => ($isDragging ? 0.4 : 1)};

  &:hover {
    border-color: #cbd5e1;
  }
`;

const ItemLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
`;

const DragHandle = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
  cursor: grab;
  padding: 2px;
  border-radius: 4px;
  transition: color 0.2s;

  &:hover {
    color: #64748b;
    background-color: #f1f5f9;
  }

  &:active {
    cursor: grabbing;
  }
`;

const ItemNumber = styled.span`
  font-size: 13px;
  color: #94a3b8;
  font-weight: 600;
`;

const ItemName = styled.span`
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
`;

const HotBadge = styled.span`
  background-color: #ff3b30;
  color: white;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
`;

const ItemRight = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;

const Count = styled.span`
  font-size: 13px;
  color: #64748b;
  margin-right: 4px;
`;

const IconButton = styled.button`
  background: none;
  border: none;
  color: ${({ $danger }) => ($danger ? '#ef4444' : '#64748b')};
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;

  &:hover {
    background-color: #f1f5f9;
  }
`;

const ToggleContainer = styled.div`
  width: 44px;
  height: 24px;
  background-color: ${({ $active }) => ($active ? '#0f172a' : '#e2e8f0')};
  border-radius: 12px;
  padding: 2px;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: background-color 0.2s;
`;

const ToggleHandle = styled.div`
  width: 20px;
  height: 20px;
  background-color: white;
  border-radius: 50%;
  transform: ${({ $active }) =>
    $active ? 'translateX(20px)' : 'translateX(0)'};
  transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
`;

export default function CategoryCard({
  title,
  items = [],
  onToggle,
  onEdit,
  onDelete,
  onAdd,
  onReorder,
}) {
  const [draggedIdx, setDraggedIdx] = useState(null);

  const handleDragStart = (e, index) => {
    setDraggedIdx(index);
    e.dataTransfer.effectAllowed = 'move';
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
  };

  const handleDrop = (e, targetIndex) => {
    e.preventDefault();
    if (draggedIdx === null || draggedIdx === targetIndex) return;
    onReorder && onReorder(draggedIdx, targetIndex);
    setDraggedIdx(null);
  };

  const handleDragEnd = () => {
    setDraggedIdx(null);
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <AddButton onClick={onAdd}>
          <Plus size={14} />
          추가
        </AddButton>
      </CardHeader>
      <ItemList>
        {items.map((item, index) => (
          <ItemRow
            key={item.id}
            $isSelected={!!item.isSelected}
            $isDragging={draggedIdx === index}
            draggable={!!onReorder}
            onDragStart={(e) => handleDragStart(e, index)}
            onDragOver={handleDragOver}
            onDrop={(e) => handleDrop(e, index)}
            onDragEnd={handleDragEnd}
          >
            <ItemLeft>
              {/* 📌 드래그용 점 아이콘 (드래그 지원하는 카테고리만) */}
              {onReorder && (
                <DragHandle title="드래그하여 순서 변경">
                  <GripVertical size={16} />
                </DragHandle>
              )}
              <ItemNumber>#{item.id}</ItemNumber>
              <ItemName>{item.name}</ItemName>
              {item.isHot && <HotBadge>HOT</HotBadge>}
            </ItemLeft>
            <ItemRight>
              <Count>{item.count}</Count>

              {/* 활성화 토글 */}
              <ToggleContainer
                $active={!!item.active}
                onClick={() => onToggle && onToggle(item.id)}
              >
                <ToggleHandle $active={!!item.active} />
              </ToggleContainer>

              {/* 수정 버튼 */}
              <IconButton onClick={() => onEdit && onEdit(item)}>
                <SquarePen size={16} />
              </IconButton>

              {/* 삭제 버튼 */}
              <IconButton
                $danger
                onClick={() => onDelete && onDelete(item.id)}
              >
                <Trash2 size={16} />
              </IconButton>
            </ItemRight>
          </ItemRow>
        ))}
      </ItemList>
    </Card>
  );
}
