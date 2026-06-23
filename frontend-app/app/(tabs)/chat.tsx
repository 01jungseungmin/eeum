import React, { useState, useCallback } from 'react';
import { 
  View, StyleSheet, FlatList, TouchableOpacity, 
  ActivityIndicator, Image, RefreshControl, Alert 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { chatApi } from '../../api/chat';

type TabType = 'MY_CHAT' | 'DISCOVER';

export default function ChatListScreen() {
  const router = useRouter();
  
  const [activeTab, setActiveTab] = useState<TabType>('MY_CHAT');
  
  const [myRooms, setMyRooms] = useState<any[]>([]);
  const [discoverRooms, setDiscoverRooms] = useState<any[]>([]); // 새로 추가될 탐색용 방 목록
  
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);

  // 화면에 들어올 때마다 현재 선택된 탭의 데이터를 불러옵니다.
  useFocusEffect(
    useCallback(() => {
      fetchData();
    }, [activeTab])
  );

  const fetchData = async () => {
    setIsLoading(true);
    try {
      if (activeTab === 'MY_CHAT') {
        const data = await chatApi.getRooms();
        setMyRooms(data);
      } else {
        // ✨ 백엔드 API가 나오기 전까지 보여줄 임시(Mock) 데이터입니다.
        // 나중에 const data = await chatApi.getDiscoverRooms(); 로 교체하시면 됩니다.
        setDiscoverRooms([
          { roomId: 991, name: '역삼동 맛집 탐방 파티', participantCount: 12, imageUrl: null },
          { roomId: 992, name: '주말 아침 러닝 모임 🏃‍♂️', participantCount: 5, imageUrl: null },
        ]);
      }
    } catch (error) {
      console.error('목록 로딩 에러:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const onRefresh = async () => {
    setIsRefreshing(true);
    await fetchData();
    setIsRefreshing(false);
  };

  // 🎯 탐색 탭에서 방을 눌렀을 때 실행되는 '자동 입장 및 이동' 로직
  const handleJoinAndEnter = async (roomId: number) => {
    try {
      console.log(`${roomId}번 방 입장 처리 중...`);
      // 우리가 스웨거에서 수동으로 했던 입장 API를 여기서 몰래 호출합니다.
      await chatApi.joinRoom(roomId); 
      // 입장 성공 후 채팅방으로 이동!
      router.push(`/chat/${roomId}` as any);
    } catch (error: any) {
      if (error.response?.status === 409 || error.response?.status === 400) {
        // 이미 참여 중인 방이라면 그냥 이동시킵니다.
        router.push(`/chat/${roomId}` as any);
      } else {
        Alert.alert('알림', '채팅방에 입장할 수 없습니다.');
      }
    }
  };

  // 리스트 아이템 렌더링 함수
  const renderRoom = ({ item }: { item: any }) => {
    const isDiscoverTab = activeTab === 'DISCOVER';
    const timeString = item.lastMessageCreatedAt 
      ? new Date(item.lastMessageCreatedAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
      : '';

    return (
      <TouchableOpacity 
        style={styles.roomItem} 
        activeOpacity={0.7}
        onPress={() => {
          if (isDiscoverTab) {
            handleJoinAndEnter(item.roomId); // 탐색 탭: 입장 후 이동
          } else {
            router.push(`/chat/${item.roomId}` as any); // 내 채팅 탭: 바로 이동
          }
        }}
      >
        <View style={styles.roomImageContainer}>
          {item.imageUrl ? (
            <Image source={{ uri: item.imageUrl }} style={styles.roomImage} />
          ) : (
            <View style={styles.placeholderImage}>
              <Ionicons name={isDiscoverTab ? "search" : "people"} size={24} color="#999" />
            </View>
          )}
        </View>

        <View style={styles.roomInfo}>
          <View style={styles.roomHeaderRow}>
            <Text fontWeight="bold" style={styles.roomName} numberOfLines={1}>
              {item.name || '이름 없는 채팅방'}
            </Text>
            {/* 참여 인원 표시 */}
            {(item.participantCount > 0 || item.participantsCount > 0) && (
              <Text style={styles.participantsCount}>
                {item.participantCount || item.participantsCount}명
              </Text>
            )}
            {!isDiscoverTab && <Text style={styles.timeText}>{timeString}</Text>}
          </View>
          
          <View style={styles.roomFooterRow}>
            <Text style={styles.lastMessage} numberOfLines={2}>
              {isDiscoverTab 
                ? '새로운 동네 이웃들과 대화를 나눠보세요!' 
                : (item.lastMessageContent || '아직 대화가 없습니다.')}
            </Text>
            
            {!isDiscoverTab && item.unreadCount > 0 && (
              <View style={styles.unreadBadge}>
                <Text fontWeight="bold" style={styles.unreadText}>
                  {item.unreadCount > 99 ? '99+' : item.unreadCount}
                </Text>
              </View>
            )}
          </View>
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* ✨ 헤더 & 방 만들기 버튼 */}
      <View style={styles.header}>
        <Text fontWeight="bold" style={styles.headerTitle}>동네 모임</Text>
        <TouchableOpacity onPress={() => router.push('/chat/create' as any)}>
          <Ionicons name="add-circle" size={28} color="#1B854A" />
        </TouchableOpacity>
      </View>

      {/* ✨ 탭 전환 버튼 (내 채팅 / 동네 모임 찾기) */}
      <View style={styles.tabContainer}>
        <TouchableOpacity 
          style={[styles.tabButton, activeTab === 'MY_CHAT' && styles.activeTabButton]}
          onPress={() => setActiveTab('MY_CHAT')}
        >
          <Text fontWeight={activeTab === 'MY_CHAT' ? "bold" : "normal"} 
                style={[styles.tabText, activeTab === 'MY_CHAT' && styles.activeTabText]}>
            내 채팅방
          </Text>
        </TouchableOpacity>
        <TouchableOpacity 
          style={[styles.tabButton, activeTab === 'DISCOVER' && styles.activeTabButton]}
          onPress={() => setActiveTab('DISCOVER')}
        >
          <Text fontWeight={activeTab === 'DISCOVER' ? "bold" : "normal"} 
                style={[styles.tabText, activeTab === 'DISCOVER' && styles.activeTabText]}>
            새로운 모임 찾기
          </Text>
        </TouchableOpacity>
      </View>

      {/* 목록 영역 */}
      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#1B854A" /></View>
      ) : (
        <FlatList
          data={activeTab === 'MY_CHAT' ? myRooms : discoverRooms}
          keyExtractor={(item, index) => item.roomId?.toString() || index.toString()}
          renderItem={renderRoom}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
          refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor="#1B854A" />}
          ListEmptyComponent={
            <View style={styles.centerEmpty}>
              <Ionicons name="chatbubbles-outline" size={60} color="#DDD" style={{ marginBottom: 16 }} />
              <Text style={styles.emptyText}>
                {activeTab === 'MY_CHAT' ? '참여 중인 채팅방이 없습니다.' : '현재 동네에 개설된 모임이 없습니다.'}
              </Text>
            </View>
          }
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFF' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  centerEmpty: { flex: 1, justifyContent: 'center', alignItems: 'center', marginTop: 100 },
  
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingTop: 15, paddingBottom: 10 },
  headerTitle: { fontSize: 24, color: '#333' },

  tabContainer: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  tabButton: { flex: 1, paddingVertical: 14, alignItems: 'center' },
  activeTabButton: { borderBottomWidth: 2, borderBottomColor: '#1B854A' },
  tabText: { fontSize: 15, color: '#888' },
  activeTabText: { color: '#1B854A' },

  listContainer: { paddingBottom: 20, flexGrow: 1 },
  
  roomItem: { flexDirection: 'row', paddingHorizontal: 20, paddingVertical: 16, borderBottomWidth: 1, borderBottomColor: '#F5F5F5', backgroundColor: '#FFF' },
  roomImageContainer: { marginRight: 15 },
  roomImage: { width: 50, height: 50, borderRadius: 20, backgroundColor: '#F0F0F0' },
  placeholderImage: { width: 50, height: 50, borderRadius: 20, backgroundColor: '#F5F6F8', justifyContent: 'center', alignItems: 'center', borderWidth: 1, borderColor: '#EEE' },
  
  roomInfo: { flex: 1, justifyContent: 'center' },
  roomHeaderRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 4 },
  roomName: { fontSize: 16, color: '#333', flexShrink: 1 },
  participantsCount: { fontSize: 13, color: '#1B854A', marginLeft: 6 },
  timeText: { fontSize: 12, color: '#999', marginLeft: 'auto' },
  
  roomFooterRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  lastMessage: { fontSize: 14, color: '#666', flex: 1, marginRight: 10 },
  
  unreadBadge: { backgroundColor: '#FF5252', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 10, minWidth: 20, alignItems: 'center', justifyContent: 'center' },
  unreadText: { color: '#FFF', fontSize: 11 },
  emptyText: { fontSize: 15, color: '#888' },
});