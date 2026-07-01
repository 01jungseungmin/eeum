import React from 'react';
import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export default function TabLayout() {
  const insets = useSafeAreaInsets(); 

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