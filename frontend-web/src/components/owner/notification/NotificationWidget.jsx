import styled from "styled-components";
import { Volume2, Bell, Info } from "lucide-react";

const RightSection = styled.aside`
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const WidgetCard = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 20px;
  border: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const WidgetHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
`;

const WidgetTitle = styled.h3`
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
`;

const SubText = styled.span`
  font-size: 13px;
  color: #6b7280;
`;

const ToggleSwitch = styled.label`
  position: relative;
  display: inline-block;
  width: 46px;
  height: 24px;

  input {
    opacity: 0;
    width: 0;
    height: 0;
  }

  input:checked + span {
    background-color: #10b981;
  }

  input:checked + span:before {
    transform: translateX(22px);
  }
`;

const Slider = styled.span`
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #d1d5db;
  transition: 0.3s;
  border-radius: 24px;

  &:before {
    position: absolute;
    content: "";
    height: 18px;
    width: 18px;
    left: 3px;
    bottom: 3px;
    background-color: white;
    transition: 0.3s;
    border-radius: 50%;
  }
`;

const StatGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
`;

const StatBox = styled.div`
  background: #f9fafb;
  padding: 14px;
  border-radius: 10px;
  text-align: center;
`;

const StatValue = styled.div`
  font-size: 20px;
  font-weight: 700;
  color: ${(props) => props.$color || "#111827"};
`;

const StatLabel = styled.div`
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
`;

const NoticeItem = styled.div`
  font-size: 13px;
  color: #4b5563;
  line-height: 1.5;
  background-color: #f0fdf4;
  border: 1px solid #dcfce7;
  padding: 12px;
  border-radius: 8px;
`;

const NotificationWidget = ({
  isGlobalNotificationOn,
  onToggleGlobalNotification,
  unreadCount,
  totalCount,
}) => {
  return (
    <RightSection>
      <WidgetCard>
        <WidgetHeader>
          <WidgetTitle>
            <Volume2 size={18} color="#10B981" />
            전체 알림 설정
          </WidgetTitle>
          <ToggleSwitch>
            <input
              type="checkbox"
              checked={isGlobalNotificationOn}
              onChange={onToggleGlobalNotification}
            />
            <Slider />
          </ToggleSwitch>
        </WidgetHeader>
        <SubText>
          {isGlobalNotificationOn ? "모든 알림 수신 중" : "모든 알림 차단 중"}
        </SubText>
      </WidgetCard>

      <WidgetCard>
        <WidgetTitle>
          <Bell size={18} color="#3B82F6" />
          알림 요약 현황
        </WidgetTitle>
        <StatGrid>
          <StatBox>
            <StatValue $color="#EF4444">{unreadCount}</StatValue>
            <StatLabel>읽지 않음</StatLabel>
          </StatBox>
          <StatBox>
            <StatValue>{totalCount - unreadCount}</StatValue>
            <StatLabel>읽음</StatLabel>
          </StatBox>
        </StatGrid>
      </WidgetCard>

      <WidgetCard>
        <WidgetTitle>
          <Info size={18} color="#059669" />
          시스템 안내
        </WidgetTitle>
        <NoticeItem>
          📢 실시간 주문 및 예약 알림은 브라우저 권한 설정이 켜져 있어야 정상
          발송됩니다.
        </NoticeItem>
      </WidgetCard>
    </RightSection>
  );
};

export default NotificationWidget;
