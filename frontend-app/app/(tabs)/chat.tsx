import React, { useState, useCallback } from 'react';
import { 
  View, StyleSheet, FlatList, TouchableOpacity, 
  ActivityIndicator, Image, RefreshControl 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { chatApi } from '../../api/chat';

export default function ChatListScreen() {
  const router = useRouter();
  
  // 상태가 아주 심플해졌습니다. '내 채팅방' 목록만 관리합니다.
  const [myRooms, setMyRooms] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);

  // 화면에 들어올 때마다 데이터를 새로고침합니다.
  useFocusEffect(
    useCallback(() => {
      fetchData();
    }, [])
  );

  const fetchData = async () => {
    setIsLoading(true);
    try {
      // 백엔드에서 내가 참여 중인 채팅방 목록만 가져옵니다.
      const data = await chatApi.getRooms();
      setMyRooms(data);
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

  const renderRoom = ({ item }: { item: any }) => {
    const timeString = item.lastMessageCreatedAt 
      ? new Date(item.lastMessageCreatedAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
      : '';

    return (
      <TouchableOpacity 
        style={styles.roomItem} 
        activeOpacity={0.7}
        // 이제 복잡한 입장 로직 없이, 클릭하면 무조건 해당 방으로 바로 이동합니다.
        onPress={() => router.push(`/chat/${item.roomId}` as any)}
      >
        <View style={styles.roomImageContainer}>
          {item.imageUrl ? (
            <Image source={{ uri: item.imageUrl }} style={styles.roomImage} />
          ) : (
            // 상점 채팅방 느낌이 나도록 기본 아이콘을 상점 모양으로 바꿨습니다.
            <View style={styles.placeholderImage}>
              <Ionicons name="storefront" size={24} color="#999" />
            </View>
          )}
        </View>

        <View style={styles.roomInfo}>
          <View style={styles.roomHeaderRow}>
            <Text fontWeight="bold" style={styles.roomName} numberOfLines={1}>
              {item.name || '상점 단체 채팅방'}
            </Text>
            {(item.participantCount > 0 || item.participantsCount > 0) && (
              <Text style={styles.participantsCount}>
                {item.participantCount || item.participantsCount}명
              </Text>
            )}
            <Text style={styles.timeText}>{timeString}</Text>
          </View>
          
          <View style={styles.roomFooterRow}>
            <Text style={styles.lastMessage} numberOfLines={2}>
              {item.lastMessageContent || '아직 대화가 없습니다.'}
            </Text>
            
            {item.unreadCount > 0 && (
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
      {/* 헤더: + 버튼을 제거하고 심플하게 '채팅' 타이틀만 남겼습니다. */}
      <View style={styles.header}>
        <Text fontWeight="bold" style={styles.headerTitle}>채팅</Text>
      </View>

      {/* 목록 영역 */}
      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#1B854A" /></View>
      ) : (
        <FlatList
          data={myRooms}
          keyExtractor={(item, index) => item.roomId?.toString() || index.toString()}
          renderItem={renderRoom}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
          refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor="#1B854A" />}
          ListEmptyComponent={
            <View style={styles.centerEmpty}>
              <Ionicons name="chatbubbles-outline" size={60} color="#DDD" style={{ marginBottom: 16 }} />
              <Text style={styles.emptyText}>참여 중인 채팅방이 없습니다.</Text>
              <Text style={styles.emptySubText}>상점의 단체 채팅에 참여해보세요!</Text>
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
  
  header: { paddingHorizontal: 20, paddingTop: 15, paddingBottom: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  headerTitle: { fontSize: 24, color: '#333' },

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
  emptyText: { fontSize: 16, color: '#555', fontWeight: 'bold', marginBottom: 6 },
  emptySubText: { fontSize: 14, color: '#888' },
});