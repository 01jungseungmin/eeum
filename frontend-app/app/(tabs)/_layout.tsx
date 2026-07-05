import React, { useEffect, useState } from 'react';
import { Tabs, usePathname } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { chatApi } from '../../api/chat';

export default function TabLayout() {
  const insets = useSafeAreaInsets(); 
  const pathname = usePathname();

  const [unreadChatCount, setUnreadChatCount] = useState(0);

  // 유저가 화면을 이동할 때마다(채팅방에서 나오거나 탭을 바꿀 때) 뱃지 숫자를 새로고침합니다.
  useEffect(() => {
    const fetchUnreadChats = async () => {
      try {
        const res = await chatApi.getUnreadCount();
        
        const actualCount = typeof res === 'number' ? res : (res?.unreadCount || res?.count || 0);
        
        setUnreadChatCount(Number(actualCount));
      } catch (error) {
        console.log('안 읽은 채팅 수 로드 실패:', error);
      }
    };

    fetchUnreadChats();
  }, [pathname]);

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveBackgroundColor: '#00A859',
        tabBarInactiveBackgroundColor: '#ffffff',
        tabBarActiveTintColor: '#ffffff', 
        tabBarInactiveTintColor: '#555555', 
        
        tabBarStyle: {
          height: 65 + insets.bottom, 
          paddingBottom: insets.bottom, 
        },
        
        tabBarItemStyle: {
          height: '100%',
          justifyContent: 'center',
        },
        
        tabBarLabelStyle: {
          fontSize: 12,
          fontWeight: 'bold',
          marginBottom: 4,
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: '홈',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? "home" : "home-outline"} size={26} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="community" 
        options={{
          title: '커뮤니티',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? "people" : "people-outline"} size={26} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="chat"
        options={{
          title: '이음톡',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? "chatbubbles" : "chatbubbles-outline"} size={26} color={color} />
          ),
          // 정상적으로 변환된 숫자가 0보다 클 때만 뱃지를 표시합니다.
          tabBarBadge: unreadChatCount > 0 ? (unreadChatCount > 99 ? '99+' : unreadChatCount) : undefined,
          tabBarBadgeStyle: { backgroundColor: '#FF5252', color: '#FFF', fontSize: 10, minWidth: 16 },
        }}
      />
      <Tabs.Screen
        name="map"
        options={{
          title: '지도',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? "map" : "map-outline"} size={26} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: '마이페이지',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? "person-circle" : "person-circle-outline"} size={26} color={color} />
          ),
        }}
      />
    </Tabs>
  );
}