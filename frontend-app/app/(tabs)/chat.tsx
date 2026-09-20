import React, { useCallback, useMemo, useRef, useState } from 'react';
import {
  View, StyleSheet, FlatList, TouchableOpacity,
  ActivityIndicator, Image, RefreshControl
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { chatApi } from '../../api/chat';

type TabKey = 'STORE' | 'USED';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'STORE', label: '상점 채팅' },
  { key: 'USED', label: '중고거래 채팅' },
];

// 한 화면을 채우기에 충분한 개수. 서버가 타입별로 걸러주지 않아 앱에서 거르는데,
// 한 페이지가 통째로 다른 탭 것일 수 있어 이 수를 채울 때까지 이어서 받는다.
const MIN_VISIBLE = 10;
const MAX_PAGES_PER_FILL = 5;

const PRICE_LABEL: Record<string, string> = {
  FREE: '나눔',
  NEGOTIABLE: '가격제안',
};

const STATUS_LABEL: Record<string, string> = {
  RESERVED: '예약중',
  SOLD: '거래완료',
};

export default function ChatListScreen() {
  const router = useRouter();

  const [activeTab, setActiveTab] = useState<TabKey>('STORE');
  const [myRooms, setMyRooms] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  // 커서 페이징 상태 (cursorValue + cursorRoomId)
  const cursorRef = useRef<{ value: string | null; roomId: number | null; hasNext: boolean }>({
    value: null,
    roomId: null,
    hasNext: true,
  });

  const isUsedRoom = (room: any) => room.refType === 'USED_PRODUCT';

  const visibleRooms = useMemo(
    () => myRooms.filter(room => (activeTab === 'USED' ? isUsedRoom(room) : !isUsedRoom(room))),
    [myRooms, activeTab]
  );

  /**
   * 활성 탭에 보여줄 방이 MIN_VISIBLE개가 될 때까지 다음 페이지를 이어 받는다.
   *
   * 서버 목록은 타입 구분 없이 한 줄로 내려오므로, 한 페이지가 전부 다른 탭 것이면
   * 화면이 비어 보인다. 그러면 onEndReached도 안 불려서 스스로 회복하지 못한다.
   */
  const fillUntilEnough = useCallback(async (tab: TabKey, accumulated: any[]) => {
    let rooms = accumulated;

    for (let page = 0; page < MAX_PAGES_PER_FILL; page++) {
      const matched = rooms.filter(r => (tab === 'USED' ? isUsedRoom(r) : !isUsedRoom(r)));
      if (matched.length >= MIN_VISIBLE || !cursorRef.current.hasNext) break;

      const result = await chatApi.getRooms(cursorRef.current.value, cursorRef.current.roomId);
      rooms = [...rooms, ...result.content];
      cursorRef.current = {
        value: result.nextCursorValue,
        roomId: result.nextCursorId,
        hasNext: result.hasNext,
      };
    }

    return rooms;
  }, []);

  const fetchData = useCallback(async (tab: TabKey) => {
    setIsLoading(true);
    try {
      cursorRef.current = { value: null, roomId: null, hasNext: true };
      const result = await chatApi.getRooms();
      cursorRef.current = {
        value: result.nextCursorValue,
        roomId: result.nextCursorId,
        hasNext: result.hasNext,
      };
      setMyRooms(await fillUntilEnough(tab, result.content));
    } catch (error) {
      console.error('목록 로딩 에러:', error);
    } finally {
      setIsLoading(false);
    }
  }, [fillUntilEnough]);

  // 화면에 들어올 때마다 데이터를 새로고침합니다.
  useFocusEffect(
    useCallback(() => {
      fetchData(activeTab);
    }, [fetchData, activeTab])
  );

  const loadMoreRooms = async () => {
    if (!cursorRef.current.hasNext || isLoading || isLoadingMore) return;

    try {
      setIsLoadingMore(true);
      const result = await chatApi.getRooms(cursorRef.current.value, cursorRef.current.roomId);
      cursorRef.current = {
        value: result.nextCursorValue,
        roomId: result.nextCursorId,
        hasNext: result.hasNext,
      };
      setMyRooms(prev => [...prev, ...result.content]);
    } catch (error) {
      console.error('채팅방 목록 추가 로드 실패:', error);
    } finally {
      setIsLoadingMore(false);
    }
  };

  const onRefresh = async () => {
    setIsRefreshing(true);
    await fetchData(activeTab);
    setIsRefreshing(false);
  };

  const handleChangeTab = (tab: TabKey) => {
    if (tab === activeTab) return;
    setActiveTab(tab);
    // 받아둔 방은 그대로 두고, 새 탭 기준으로 모자라면 더 받는다.
    fillUntilEnough(tab, myRooms).then(setMyRooms).catch(() => {});
  };

  const renderRoom = ({ item }: { item: any }) => {
    const timeData = item.lastMessageAt || item.createdAt;
    const timeString = timeData
      ? new Date(timeData).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
      : '';

    const lastMsgText = item.lastMessagePreview || '아직 대화가 없습니다.';
    const used = item.usedProduct;

    // 중고 문의방은 방 이름 대신 어떤 물건 이야기인지가 중요하다.
    const title = used ? used.title : (item.name || '상점 단체 채팅방');
    const thumbnailUrl = used ? used.thumbnailUrl : item.imageUrl;
    const statusLabel = used ? STATUS_LABEL[used.status] : null;

    return (
      <TouchableOpacity
        style={styles.roomItem}
        activeOpacity={0.7}
        onPress={() => router.push(`/chat/${item.roomId}` as any)}
      >
        <View style={styles.roomImageContainer}>
          {thumbnailUrl ? (
            <Image source={{ uri: thumbnailUrl }} style={styles.roomImage} />
          ) : (
            <View style={styles.placeholderImage}>
              <Ionicons name={used ? 'pricetag' : 'storefront'} size={24} color="#999" />
            </View>
          )}
        </View>

        <View style={styles.roomInfo}>
          <View style={styles.roomHeaderRow}>
            <Text fontWeight="bold" style={styles.roomName} numberOfLines={1}>
              {title}
            </Text>
            {!used && (item.participantCount > 0 || item.participantsCount > 0) && (
              <Text style={styles.participantsCount}>
                {item.participantCount || item.participantsCount}명
              </Text>
            )}
            {!!statusLabel && (
              <View style={styles.statusBadge}>
                <Text style={styles.statusBadgeText}>{statusLabel}</Text>
              </View>
            )}
            <Text style={styles.timeText}>{timeString}</Text>
          </View>

          {used && (
            <Text style={styles.priceText}>
              {PRICE_LABEL[used.priceType] ?? `${Number(used.price ?? 0).toLocaleString()}원`}
            </Text>
          )}

          <View style={styles.roomFooterRow}>
            <Text style={styles.lastMessage} numberOfLines={2}>
              {lastMsgText}
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
      <View style={styles.header}>
        <Text fontWeight="bold" style={styles.headerTitle}>이음톡</Text>
      </View>

      <View style={styles.tabBar}>
        {TABS.map(tab => {
          const isActive = tab.key === activeTab;
          return (
            <TouchableOpacity
              key={tab.key}
              style={[styles.tab, isActive && styles.tabActive]}
              onPress={() => handleChangeTab(tab.key)}
            >
              <Text
                fontWeight={isActive ? 'bold' : undefined}
                style={[styles.tabText, isActive && styles.tabTextActive]}
              >
                {tab.label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#1B854A" /></View>
      ) : (
        <FlatList
          data={visibleRooms}
          keyExtractor={(item, index) => item.roomId?.toString() || index.toString()}
          renderItem={renderRoom}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
          refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor="#1B854A" />}
          onEndReached={loadMoreRooms}
          onEndReachedThreshold={0.4}
          ListFooterComponent={
            isLoadingMore ? <ActivityIndicator style={{ marginVertical: 20 }} color="#1B854A" /> : null
          }
          ListEmptyComponent={
            <View style={styles.centerEmpty}>
              <Ionicons
                name={activeTab === 'USED' ? 'pricetags-outline' : 'chatbubbles-outline'}
                size={60}
                color="#DDD"
                style={{ marginBottom: 16 }}
              />
              <Text style={styles.emptyText}>
                {activeTab === 'USED' ? '중고거래 채팅이 없습니다.' : '상점 채팅이 없습니다.'}
              </Text>
              <Text style={styles.emptySubText}>
                {activeTab === 'USED'
                  ? '관심 있는 중고 물건에서 채팅을 걸어보세요!'
                  : '상점의 단체 채팅에 참여해보세요!'}
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

  header: { paddingHorizontal: 20, paddingTop: 15, paddingBottom: 15 },
  headerTitle: { fontSize: 24, color: '#333' },

  tabBar: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  tab: { flex: 1, paddingVertical: 12, alignItems: 'center', borderBottomWidth: 2, borderBottomColor: 'transparent' },
  tabActive: { borderBottomColor: '#1B854A' },
  tabText: { fontSize: 15, color: '#999' },
  tabTextActive: { color: '#1B854A' },

  listContainer: { paddingBottom: 20, flexGrow: 1 },

  roomItem: { flexDirection: 'row', paddingHorizontal: 20, paddingVertical: 16, borderBottomWidth: 1, borderBottomColor: '#F5F5F5', backgroundColor: '#FFF' },
  roomImageContainer: { marginRight: 15 },
  roomImage: { width: 50, height: 50, borderRadius: 20, backgroundColor: '#F0F0F0' },
  placeholderImage: { width: 50, height: 50, borderRadius: 20, backgroundColor: '#F5F6F8', justifyContent: 'center', alignItems: 'center', borderWidth: 1, borderColor: '#EEE' },

  roomInfo: { flex: 1, justifyContent: 'center' },
  roomHeaderRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 4 },
  roomName: { fontSize: 16, color: '#333', flexShrink: 1 },
  participantsCount: { fontSize: 13, color: '#1B854A', marginLeft: 6 },
  statusBadge: { marginLeft: 6, paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, backgroundColor: '#F0F0F0' },
  statusBadgeText: { fontSize: 11, color: '#666' },
  timeText: { fontSize: 12, color: '#999', marginLeft: 'auto' },
  priceText: { fontSize: 13, color: '#1B854A', marginBottom: 4 },

  roomFooterRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  lastMessage: { fontSize: 14, color: '#666', flex: 1, marginRight: 10 },

  unreadBadge: { backgroundColor: '#FF5252', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 10, minWidth: 20, alignItems: 'center', justifyContent: 'center' },
  unreadText: { color: '#FFF', fontSize: 11 },
  emptyText: { fontSize: 16, color: '#555', fontWeight: 'bold', marginBottom: 6 },
  emptySubText: { fontSize: 14, color: '#888' },
});
