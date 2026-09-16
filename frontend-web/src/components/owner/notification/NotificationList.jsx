import styled from "styled-components";
import { CheckCheck, Trash2 } from "lucide-react";
import NotificationItem from "./NotificationItem";

const LeftSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const HeaderSection = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
  padding: 20px 24px;
  border-radius: 16px;
  border: 1px solid #e5e7eb;
`;

const HeaderLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;

// 헤더 우측 버튼들을 묶는 컨테이너
const HeaderRight = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const MainTitle = styled.h2`
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
`;

const Badge = styled.span`
  background-color: #ef4444;
  color: white;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
`;

const SubText = styled.span`
  font-size: 13px;
  color: #6b7280;
`;

const ActionButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background-color: #ffffff;
  color: #374151;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background-color: #f9fafb;
    border-color: #d1d5db;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

// 전체 삭제 버튼 전용 레드 호버 스타일
const DeleteAllButton = styled(ActionButton)`
  color: #ef4444;
  border-color: #fecdd3;
  background-color: #fff1f2;

  &:hover {
    background-color: #ffe4e6;
    border-color: #fda4af;
  }
`;

const FilterGroup = styled.div`
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
`;

const FilterChip = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 20px;
  border: 1px solid ${(props) => (props.$active ? "#10B981" : "#E5E7EB")};
  background-color: ${(props) => (props.$active ? "#10B981" : "#ffffff")};
  color: ${(props) => (props.$active ? "#ffffff" : "#4B5563")};
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
`;

const ListSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const EmptyState = styled.div`
  text-align: center;
  padding: 60px 0;
  color: #9ca3af;
  font-size: 14px;
  background: #ffffff;
  border-radius: 16px;
  border: 1px dashed #e5e7eb;
`;

const FILTERS = [
  { label: "전체", value: "ALL" },
  { label: "주문", value: "ORDER" },
  { label: "채팅", value: "CHAT" },
  { label: "리뷰", value: "REVIEW" },
  { label: "예약", value: "RESERVATION" },
  { label: "상품", value: "PRODUCT" },
  { label: "시스템", value: "SYSTEM" },
];

const NotificationList = ({
  notifications,
  selectedFilter,
  onSelectFilter,
  onMarkAllAsRead,
  onDeleteNotification,
  onDeleteAllNotifications, // ✨ 전체 삭제 핸들러 Props 추가
  onItemClick,
  unreadCount,
  totalElements,
  loading,
}) => {
  // selectedFilter에 맞춰 프론트에서 즉시 필터링
  const filteredNotifications = notifications.filter((item) => {
    if (selectedFilter === "ALL") return true;
    // 백엔드 Enum 타입과 필터 값 비교
    return (
      item.refType?.includes(selectedFilter) ||
      item.type?.includes(selectedFilter)
    );
  });
  return (
    <LeftSection>
      <HeaderSection>
        <HeaderLeft>
          <MainTitle>
            알림 <Badge>{unreadCount}</Badge>
          </MainTitle>
          <SubText>| 총 {totalElements}개의 알림이 있습니다</SubText>
        </HeaderLeft>

        <HeaderRight>
          <ActionButton
            onClick={onMarkAllAsRead}
            disabled={notifications.length === 0 || unreadCount === 0}
          >
            <CheckCheck size={16} />
            모두 읽음
          </ActionButton>

          {/* ✨ 전체 삭제 버튼 추가 */}
          <DeleteAllButton
            onClick={onDeleteAllNotifications}
            disabled={notifications.length === 0}
          >
            <Trash2 size={16} />
            전체 삭제
          </DeleteAllButton>
        </HeaderRight>
      </HeaderSection>

      <FilterGroup>
        {FILTERS.map((filter) => (
          <FilterChip
            key={filter.value}
            $active={selectedFilter === filter.value}
            onClick={() => onSelectFilter(filter.value)}
          >
            {filter.label}
          </FilterChip>
        ))}
      </FilterGroup>

      <ListSection>
        {loading ? (
          <EmptyState>알림 목록을 불러오는 중입니다...</EmptyState>
        ) : filteredNotifications.length > 0 ? (
          filteredNotifications.map((item) => (
            <NotificationItem
              key={item.notificationId}
              item={item}
              onDelete={onDeleteNotification}
              onClick={onItemClick}
            />
          ))
        ) : (
          <EmptyState>해당 카테고리의 알림이 없습니다.</EmptyState>
        )}
      </ListSection>
    </LeftSection>
  );
};

export default NotificationList;
