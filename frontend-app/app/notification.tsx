import React, { useState, useEffect, useCallback } from 'react';
import { 
  StyleSheet, View, FlatList, TouchableOpacity, 
  ActivityIndicator, Alert, RefreshControl 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';

import { Text } from '../components/CustomText';
import { notificationApi, NotificationItem } from '../api/notification';
import { getNotificationRoute } from '../utils/notificationRoute';


// 백엔드 DB 타입에 맞춘 카테고리 탭 데이터 구성
const NOTI_TABS = [
  { id: 'ALL', name: '전체' },
  { id: 'ORDER', name: '주문' },
  { id: 'RESERVATION', name: '예약' },
  { id: 'REVIEW', name: '리뷰' },
  { id: 'SYSTEM', name: '문의/신고' },
  { id: 'COMMUNITY', name: '커뮤니티' },
];

export default function NotificationsScreen() {
  const router = useRouter();
  
  // 상태 관리
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [activeTab, setActiveTab] = useState('ALL');
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const [page, setPage] = useState(0); 
  const [isLastPage, setIsLastPage] = useState(false);
  const [isFetchingMore, setIsFetchingMore] = useState(false);

  // 알림 목록 가져오기 (카테고리 필터 반영)
  const fetchNotifications = async (tabId: string, pageNum: number, isRefresh = false) => {
    try {
      if (isRefresh) setIsRefreshing(true);
      else if (pageNum === 0) setIsLoading(true);
      else setIsFetchingMore(true);

      const res = await notificationApi.getNotifications(tabId, pageNum);
      const dataInfo = res?.data || res;
      
      const realData = dataInfo?.content || [];
      const last = dataInfo?.last ?? true; // 백엔드에서 주는 마지막 페이지 여부

      if (pageNum === 0) {
        setNotifications(realData); // 1페이지면 데이터 덮어쓰기
      } else {
        setNotifications(prev => [...prev, ...realData]); // 다음 페이지면 기존 배열에 이어붙이기
      }
      
      setIsLastPage(last);
      setPage(pageNum);

    } catch (error) {
      console.error('전체 알림 로딩 에러:', error);
      if (pageNum === 0) {
        setNotifications([]);
      }
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
      setIsFetchingMore(false);
    }
  };

  // 탭이 변경될 때마다 해당 카테고리의 알림을 새로 호출
  // 탭 변경 시 1페이지(0)부터 다시 불러오기
  useEffect(() => {
    fetchNotifications(activeTab, 0);
  }, [activeTab]);

  // 화면을 위로 당겨서 새로고침
  const handleRefresh = () => {
    fetchNotifications(activeTab, 0, true);
  };

  // 스크롤이 맨 아래에 닿았을 때 다음 페이지 불러오기
  const handleLoadMore = () => {
    if (!isLastPage && !isFetchingMore && !isLoading) {
      fetchNotifications(activeTab, page + 1);
    }
  };

  // 개별 알림 클릭 (읽음 처리 후 해당 도메인 화면으로 라우팅)
  const handlePressItem = async (item: NotificationItem) => {
    const isUnread = item.Read === false || item.Read === 0 || item.Read === '0';
    
    if (isUnread) {
      try {
        // 단건 읽음 처리 API 호출
        await notificationApi.markAsRead(item.notificationId);
        setNotifications(prev => prev.map(n => 
          n.notificationId === item.notificationId ? { ...n, Read: 1 } : n
        ));
      } catch (error) {
        console.error('읽음 처리 실패:', error);
      }
    }

    const route = getNotificationRoute(item);
    if (!route) {
      Alert.alert('알림', '이 알림은 이동할 화면이 없습니다.');
      return;
    }
    router.push(route as any);
  };


  // 단건 알림 삭제 (DELETE /notifications/{id})
  const handleDeleteItem = async (id: number) => {
    try {
      await notificationApi.deleteNotification(id);
      setNotifications(prev => prev.filter(n => n.notificationId !== id));
    } catch (error) {
      Alert.alert('오류', '알림 삭제에 실패했습니다.');
    }
  };

  // 전체 읽음 처리 (PATCH /notifications/read/all)
  const handleMarkAllRead = async () => {
    try {
      await notificationApi.markAllAsRead();
      setNotifications(prev => prev.map(n => ({ ...n, Read: 1 })));
      Alert.alert('알림', '모든 알림이 읽음 처리되었습니다.');
    } catch (error) {
      Alert.alert('오류', '처리 중 문제가 발생했습니다.');
    }
  };

  // 전체 삭제 처리 (DELETE /notifications/all)
  const handleDeleteAll = () => {
    Alert.alert('알림함 비우기', '모든 알림 내역을 삭제하시겠습니까?', [
      { text: '취소', style: 'cancel' },
      { text: '전체 삭제', style: 'destructive', onPress: async () => {
        try {
          await notificationApi.deleteAllNotifications();
          setNotifications([]);
        } catch (error) {
          Alert.alert('오류', '알림을 비우지 못했습니다.');
        }
      }}
    ]);
  };

  // 알림 아이템 렌더링
  const renderItem = ({ item }: { item: NotificationItem }) => {
    const isUnread = item.Read === false || item.Read === 0 || item.Read === '0';

    const cleanContent = item.content ? item.content.replace(/\(ORD-[^)]+\)/g, '') : '';

    return (
      <TouchableOpacity 
        style={[styles.card, isUnread && styles.unreadCard]} 
        onPress={() => handlePressItem(item)}
        activeOpacity={0.9}
      >
        <View style={styles.cardHeader}>
          <View style={styles.titleContainer}>
            {isUnread && <View style={styles.unreadDot} />}
            <Text fontWeight={isUnread ? "bold" : "normal"} style={styles.titleText} numberOfLines={1}>
              {item.title}
            </Text>
          </View>
          <TouchableOpacity onPress={() => handleDeleteItem(item.notificationId)} style={styles.closeBtn}>
            <Ionicons name="close" size={18} color="#999" />
          </TouchableOpacity>
        </View>

        <Text style={styles.contentText}>{cleanContent}</Text>
        <Text style={styles.dateText}>{item.createdAt.replace('T', ' ').substring(0, 16)}</Text>
      </TouchableOpacity>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 상단 헤더 */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
            <Ionicons name="chevron-back" size={26} color="#000" />
          </TouchableOpacity>
          <Text fontWeight="bold" style={styles.headerTitle}>알림 센터</Text>
        </View>

        <View style={styles.headerRight}>
          {/* 전체 읽음 */}
          <TouchableOpacity onPress={handleMarkAllRead} style={styles.headerIcon}>
            <Ionicons name="checkmark-done-outline" size={22} color="#333" />
          </TouchableOpacity>
          {/* 전체 삭제 */}
          <TouchableOpacity onPress={handleDeleteAll} style={styles.headerIcon}>
            <Ionicons name="trash-outline" size={20} color="#333" />
          </TouchableOpacity>
        </View>
      </View>

      {/* 카테고리 필터 탭 바 (스크롤 가능하도록 처리) */}
      <View style={styles.tabBar}>
        <FlatList
          horizontal
          showsHorizontalScrollIndicator={false}
          data={NOTI_TABS}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => (
            <TouchableOpacity 
              style={[styles.tabItem, activeTab === item.id && styles.activeTabItem]}
              onPress={() => setActiveTab(item.id)}
            >
              <Text 
                fontWeight={activeTab === item.id ? "bold" : "normal"}
                style={[styles.tabText, activeTab === item.id && styles.activeTabText]}
              >
                {item.name}
              </Text>
            </TouchableOpacity>
          )}
        />
      </View>

      {/* 리스트 영역 */}
      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#00A859" /></View>
      ) : notifications.length === 0 ? (
        <View style={styles.center}>
          <Ionicons name="notifications-off-outline" size={50} color="#CCC" style={{ marginBottom: 12 }} />
          <Text style={styles.emptyText}>해당 카테고리의 알림이 없습니다.</Text>
        </View>
      ) : (
        <FlatList
          data={notifications}
          keyExtractor={(item) => item.notificationId.toString()}
          renderItem={renderItem}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
          // Pull to Refresh 기능 연결
          refreshControl={
            <RefreshControl refreshing={isRefreshing} onRefresh={handleRefresh} colors={['#00A859']} />
          }
          onEndReached={handleLoadMore}
          onEndReachedThreshold={0.5} // 화면의 50% 정도 남았을 때 미리 다음 페이지 호출
          ListFooterComponent={isFetchingMore ? <ActivityIndicator style={{ padding: 20 }} color="#00A859" /> : null}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8F9FA' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  
  header: { 
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', 
    paddingHorizontal: 15, paddingVertical: 15, backgroundColor: '#fff' 
  },
  headerLeft: { flexDirection: 'row', alignItems: 'center' },
  backButton: { marginRight: 10 },
  headerTitle: { fontSize: 18, color: '#000' },
  headerRight: { flexDirection: 'row', alignItems: 'center' },
  headerIcon: { marginLeft: 16, padding: 2 },

  // 탭 스타일
  tabBar: { backgroundColor: '#fff', borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  tabItem: { paddingHorizontal: 18, paddingVertical: 14, borderBottomWidth: 2, borderBottomColor: 'transparent' },
  activeTabItem: { borderBottomColor: '#00A859' },
  tabText: { fontSize: 14, color: '#666' },
  activeTabText: { color: '#00A859' },

  listContainer: { padding: 15 },
  emptyText: { fontSize: 15, color: '#999' },

  // 알림 카드 스타일
  card: { 
    backgroundColor: '#fff', padding: 16, borderRadius: 10, marginBottom: 12,
    borderWidth: 1, borderColor: '#EDEFEF',
    shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.03, shadowRadius: 3, elevation: 1
  },
  unreadCard: { backgroundColor: '#F4FAF6', borderColor: '#D1EADF' }, // 안 읽은 알림 강조 컬러
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
  titleContainer: { flexDirection: 'row', alignItems: 'center', flex: 1, paddingRight: 10 },
  unreadDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#FF5252', marginRight: 6 },
  titleText: { fontSize: 14, color: '#222' },
  closeBtn: { padding: 2 },
  contentText: { fontSize: 13, color: '#555', lineHeight: 18, marginBottom: 8 },
  dateText: { fontSize: 11, color: '#999' }
});