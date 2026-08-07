import styled from "styled-components";
import {
  ShoppingBag,
  MessageSquare,
  Star,
  Calendar,
  AlertTriangle,
  Cog,
  Trash2,
} from "lucide-react";

const NotificationCard = styled.div`
  background: ${(props) => (props.$unread ? "#ffffff" : "#f9fafb")};
  border-radius: 14px;
  padding: 18px 22px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: 1px solid ${(props) => (props.$unread ? "#a7f3d0" : "#e5e7eb")};
  box-shadow: ${(props) =>
    props.$unread ? "0 2px 8px rgba(16, 185, 129, 0.08)" : "none"};
  transition: all 0.2s ease-in-out;
  cursor: pointer;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
    border-color: ${(props) => (props.$unread ? "#10b981" : "#d1d5db")};
    transform: translateY(-1px);
  }
`;

const CardLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  min-width: 0;
`;

const CategoryIconBox = styled.div`
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: ${(props) => props.$bg || "#f3f4f6"};
  flex-shrink: 0;
`;

const CardContent = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-width: 0;
`;

const CardHeader = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const ItemTitle = styled.h4`
  font-size: 15px;
  font-weight: ${(props) => (props.$unread ? "700" : "600")};
  color: ${(props) => (props.$unread ? "#111827" : "#374151")};
  margin: 0;
`;

const UnreadDot = styled.span`
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #10b981;
  flex-shrink: 0;
`;

const ItemBody = styled.p`
  font-size: 14px;
  color: ${(props) => (props.$unread ? "#374151" : "#6b7280")};
  margin: 0;
  line-height: 1.45;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const MetaInfo = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
`;

const CategoryTag = styled.span`
  font-size: 11px;
  font-weight: 600;
  color: ${(props) => props.$color || "#059669"};
  background-color: ${(props) => props.$bg || "#ecfdf5"};
  padding: 2px 8px;
  border-radius: 6px;
`;

const TimeText = styled.span`
  font-size: 12px;
  color: #9ca3af;
`;

const DeleteButton = styled.button`
  background: none;
  border: none;
  color: #d1d5db;
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
  margin-left: 12px;

  &:hover {
    color: #ef4444;
    background-color: #fef2f2;
  }
`;

// 백엔드 Enum (refType 또는 type 기준) 매핑
const getCategoryConfig = (refType, type) => {
  const categoryKey = refType || type;
  if (categoryKey?.includes("ORDER")) {
    return {
      label: "주문",
      icon: <ShoppingBag size={20} color="#2563EB" />,
      iconBg: "#EFF6FF",
      tagColor: "#2563EB",
      tagBg: "#EFF6FF",
    };
  }
  if (categoryKey?.includes("CHAT")) {
    return {
      label: "채팅",
      icon: <MessageSquare size={20} color="#10B981" />,
      iconBg: "#ECFDF5",
      tagColor: "#059669",
      tagBg: "#ECFDF5",
    };
  }
  if (categoryKey?.includes("REVIEW")) {
    return {
      label: "리뷰",
      icon: <Star size={20} color="#D97706" />,
      iconBg: "#FEF3C7",
      tagColor: "#D97706",
      tagBg: "#FEF3C7",
    };
  }
  if (categoryKey?.includes("RESERVATION")) {
    return {
      label: "예약",
      icon: <Calendar size={20} color="#DB2777" />,
      iconBg: "#FCE7F3",
      tagColor: "#DB2777",
      tagBg: "#FCE7F3",
    };
  }
  if (categoryKey?.includes("PRODUCT")) {
    return {
      label: "상품",
      icon: <AlertTriangle size={20} color="#DC2626" />,
      iconBg: "#FEF2F2",
      tagColor: "#DC2626",
      tagBg: "#FEF2F2",
    };
  }
  return {
    label: "시스템",
    icon: <Cog size={20} color="#4B5563" />,
    iconBg: "#F3F4F6",
    tagColor: "#4B5563",
    tagBg: "#F3F4F6",
  };
};

// 날짜 포맷팅 헬퍼
const formatTimeAgo = (dateString) => {
  if (!dateString) return "";
  const date = new Date(dateString);
  const now = new Date();
  const diffMinutes = Math.floor((now - date) / (1000 * 60));

  if (diffMinutes < 1) return "방금 전";
  if (diffMinutes < 60) return `${diffMinutes}분 전`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}시간 전`;

  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays}일 전`;
};

const NotificationItem = ({ item, onDelete, onClick }) => {
  const isUnread = !item.read;
  const config = getCategoryConfig(item.refType, item.type);

  return (
    <NotificationCard
      $unread={isUnread}
      onClick={() => onClick && onClick(item)}
    >
      <CardLeft>
        <CategoryIconBox $bg={config.iconBg}>{config.icon}</CategoryIconBox>
        <CardContent>
          <CardHeader>
            <ItemTitle $unread={isUnread}>{item.title}</ItemTitle>
            {isUnread && <UnreadDot />}
          </CardHeader>
          <ItemBody $unread={isUnread}>{item.content}</ItemBody>
          <MetaInfo>
            <CategoryTag $color={config.tagColor} $bg={config.tagBg}>
              {config.label}
            </CategoryTag>
            <TimeText>{formatTimeAgo(item.createdAt)}</TimeText>
          </MetaInfo>
        </CardContent>
      </CardLeft>
      <DeleteButton
        onClick={(e) => {
          e.stopPropagation();
          onDelete(item.notificationId);
        }}
        title="알림 삭제"
        aria-label="알림 삭제"
      >
        <Trash2 size={18} />
      </DeleteButton>
    </NotificationCard>
  );
};

export default NotificationItem;
