import React from 'react';
import styled from 'styled-components';
import { Plus, SquarePen, Trash2 } from 'lucide-react';

// --- Styled Components ---
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

// 1. $isSelected 적용
const ItemRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #ffffff;
  border: 1px solid
    ${({ $isSelected }) => ($isSelected ? '#00b074' : '#f1f5f9')};
  border-radius: 12px;
  transition: border-color 0.2s;
`;

const ItemLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
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

// 2. $danger 적용
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

// 3. $active 적용
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

// 4. $active 적용
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

// --- Component ---
export default function CategoryCard({ title, items, onToggle }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <AddButton>
          <Plus size={14} />
          추가
        </AddButton>
      </CardHeader>
      <ItemList>
        {items.map((item) => (
          <ItemRow
            key={item.id}
            $isSelected={!!item.isSelected}
          >
            <ItemLeft>
              <ItemNumber>#{item.id}</ItemNumber>
              <ItemName>{item.name}</ItemName>
              {item.isHot && <HotBadge>HOT</HotBadge>}
            </ItemLeft>
            <ItemRight>
              <Count>{item.count}</Count>
              <ToggleContainer
                $active={!!item.active}
                onClick={() => onToggle(item.id)}
              >
                <ToggleHandle $active={!!item.active} />
              </ToggleContainer>
              <IconButton>
                <SquarePen size={16} />
              </IconButton>
              <IconButton $danger>
                <Trash2 size={16} />
              </IconButton>
            </ItemRight>
          </ItemRow>
        ))}
      </ItemList>
    </Card>
  );
}
