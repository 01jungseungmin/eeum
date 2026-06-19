import React, { useState } from 'react';
import { View, StyleSheet, TouchableOpacity, FlatList } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';

export default function ChatListScreen() {
  const router = useRouter();

  // (테스트용) 채팅방 더미 데이터
  const [chatRooms, setChatRooms] = useState([
    { id: '1', name: '동네 맛집 탐방방', lastMessage: '오늘 저녁은 치킨 어떠세요?', time: '오후 5:00', unread: 2 },
    { id: '2', name: '주말 풋살 모임', lastMessage: '내일 비온다는데 어쩌죠?', time: '오후 3:30', unread: 0 },
  ]);

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 1. 헤더 & 방 만들기 버튼 영역 */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>채팅</Text>
        
        <TouchableOpacity 
          style={styles.createBtn} 
          // 💡 방 만들기 화면으로 이동하는 라우팅 (나중에 write 페이지를 만들면 연결해주세요)
          onPress={() => router.push('/chat/create' as any)} 
        >
          <Ionicons name="add" size={18} color="#fff" />
          <Text style={styles.createBtnText}>방 만들기</Text>
        </TouchableOpacity>
      </View>

      {/* 2. 단체 채팅방 목록 영역 */}
      <FlatList
        data={chatRooms}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <TouchableOpacity
            style={styles.roomItem}
            // 💡 채팅방 터치 시 STOMP가 연결될 상세 화면으로 이동
            onPress={() => router.push(`/chat/${item.id}` as any)}
          >
            {/* 단체 채팅방 아이콘 */}
            <View style={styles.avatar}>
              <Ionicons name="people" size={24} color="#BBB" />
            </View>
            
            <View style={styles.roomInfo}>
              <Text style={styles.roomName} fontWeight="bold">{item.name}</Text>
              <Text style={styles.lastMessage} numberOfLines={1}>{item.lastMessage}</Text>
            </View>
            
            <View style={styles.metaInfo}>
              <Text style={styles.timeText}>{item.time}</Text>
              {item.unread > 0 && (
                <View style={styles.unreadBadge}>
                  <Text style={styles.unreadText}>{item.unread}</Text>
                </View>
              )}
            </View>
          </TouchableOpacity>
        )}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>참여 중인 채팅방이 없습니다.</Text>
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { 
    flexDirection: 'row', 
    justifyContent: 'space-between', 
    alignItems: 'center', 
    paddingHorizontal: 20, 
    paddingVertical: 15,
    borderBottomWidth: 1, 
    borderBottomColor: '#F0F0F0' 
  },
  headerTitle: { fontSize: 22, fontWeight: 'bold', color: '#333' },
  
  createBtn: { 
    flexDirection: 'row', 
    alignItems: 'center', 
    backgroundColor: '#1B854A', 
    paddingHorizontal: 12, 
    paddingVertical: 8, 
    borderRadius: 20 
  },
  createBtnText: { color: '#fff', fontSize: 13, fontWeight: 'bold', marginLeft: 4 },
  
  roomItem: { 
    flexDirection: 'row', 
    paddingVertical: 16, 
    paddingHorizontal: 20, 
    borderBottomWidth: 1, 
    borderBottomColor: '#F8F9FA', 
    alignItems: 'center' 
  },
  avatar: { 
    width: 50, 
    height: 50, 
    borderRadius: 25, 
    backgroundColor: '#F5F6F8', 
    justifyContent: 'center', 
    alignItems: 'center', 
    marginRight: 15 
  },
  roomInfo: { flex: 1, marginRight: 10 },
  roomName: { fontSize: 16, color: '#333', marginBottom: 4 },
  lastMessage: { fontSize: 14, color: '#888' },
  
  metaInfo: { alignItems: 'flex-end' },
  timeText: { fontSize: 12, color: '#AAA', marginBottom: 6 },
  unreadBadge: { 
    backgroundColor: '#E25555', 
    borderRadius: 12, 
    paddingHorizontal: 6, 
    paddingVertical: 2, 
    minWidth: 20, 
    alignItems: 'center' 
  },
  unreadText: { color: '#fff', fontSize: 11, fontWeight: 'bold' },
  
  emptyContainer: { padding: 50, alignItems: 'center' },
  emptyText: { color: '#999', fontSize: 15 }
});