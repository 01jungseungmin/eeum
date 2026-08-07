import React, { useState, useCallback } from 'react';
import { 
  View, StyleSheet, TouchableOpacity, Modal, 
  FlatList, ActivityIndicator, Alert
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';
import { useRouter, useFocusEffect } from 'expo-router';

import { notificationApi, NotificationItem } from '../../api/notification'; 
import { getNotificationRoute } from '../../utils/notificationRoute';

interface HomeHeaderProps {
  primaryRegionName: string;
  onOpenModal: () => void;
  activeTab: 'shop' | 'used';
  setActiveTab: (tab: 'shop' | 'used') => void;
  onSearch: () => void;
}

export default function HomeHeader({
  primaryRegionName,
  onOpenModal,
  activeTab,
  setActiveTab,
  onSearch
}: HomeHeaderProps) {
  const router = useRouter();

  // 알림 관련 상태 관리
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [isDropdownVisible, setIsDropdownVisible] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  // 화면에 들어올 때마다 안 읽은 알림 숫자와 최근 알림 목록을 갱신합니다.
  useFocusEffect(
    useCallback(() => {
      fetchNotificationData();
    }, [])
  );

  const fetchNotificationData = async () => {
    try {
      // 1. 알림 전체 목록 최신화
      const notiRes = await notificationApi.getNotifications();
      const notiList = notiRes?.data?.content || notiRes?.data || notiRes || [];
      
      // 2. 채팅 카테고리가 아닌 일반 알림(예약, 주문, 문의 등)만 화면 목록에 표시하기 위해 걸러냅니다
      const filteredNotiList = notiList.filter((noti: NotificationItem) => noti.type !== 'CHAT' && noti.refType !== 'CHAT');
      setNotifications(filteredNotiList);

      // 3. 일반 알림 중 안 읽은 것의 개수만 직접 셉니다!
      const unreadCountValue = filteredNotiList.filter(
        (noti: NotificationItem) => noti.Read === false || noti.Read === 0 || noti.Read === '0'
      ).length;
      setUnreadCount(unreadCountValue);

    } catch (error) {
      console.error('헤더 알림 데이터 로딩 에러:', error);
    }
  };

  const handleOpenDropdown = () => {
    setIsDropdownVisible(true);
  };

  const handleCloseDropdown = () => {
    setIsDropdownVisible(false);
  };

  const handlePressNotification = async (item: NotificationItem) => {
    console.log('🔔 알림 원본:', item);   // linkUrl / refType / refId 확인용
    handleCloseDropdown();

    if (!item.Read && item.Read !== 1 && item.Read !== '1') {
      try {
        await notificationApi.markAsRead(item.notificationId);
        setUnreadCount(prev => Math.max(0, prev - 1));
      } catch (error) {
        console.error('읽음 처리 실패', error);
      }
    }

    const route = getNotificationRoute(item);
    if (!route) {
      Alert.alert('알림', '이 알림은 이동할 화면이 없습니다.');
      return;
    }

    try {
      router.push(route as any);
    } catch (e) {
      console.error('라우팅 실패:', route, e);
      Alert.alert('오류', '해당 화면으로 이동할 수 없습니다.');
    }
  };

  // 개별 알림 렌더링
  const renderNotificationItem = ({ item }: { item: NotificationItem }) => {
    const isUnread = item.Read === false || item.Read === 0 || item.Read === '0';
    return (
      <TouchableOpacity 
        style={[styles.dropdownItem, isUnread && styles.dropdownItemUnread]} 
        onPress={() => handlePressNotification(item)}
      >
        <View style={styles.dropdownItemHeader}>
          {isUnread && <View style={styles.unreadDot} />}
          <Text fontWeight={isUnread ? "bold" : "normal"} style={styles.dropdownItemTitle} numberOfLines={1}>
            {item.title}
          </Text>
        </View>
        <Text style={styles.dropdownItemContent} numberOfLines={1}>{item.content}</Text>
        <Text style={styles.dropdownItemTime}>
          {item.createdAt.replace('T', ' ').substring(0, 16)}
        </Text>
      </TouchableOpacity>
    );
  };
  


  return (
    <View style={styles.headerContainer}>
      <View style={styles.headerTop}>
        <TouchableOpacity style={styles.locationSelector} onPress={onOpenModal}>
          <Ionicons name="location-sharp" size={18} color="#00A859" />
          <Text style={styles.headerLocationText}>{primaryRegionName}</Text>
          <Ionicons name="chevron-down" size={16} color="#333" />
        </TouchableOpacity>
        
        <View style={styles.headerIcons}>
          {/* 1. 알림 종 아이콘 및 빨간 뱃지 UI */}
          <TouchableOpacity style={styles.iconWrapper} onPress={handleOpenDropdown}>
            <Ionicons name="notifications-outline" size={24} color="#333" />
            {unreadCount > 0 && (
              <View style={styles.badge}>
                <Text fontWeight="bold" style={styles.badgeText}>
                  {unreadCount > 99 ? '99+' : unreadCount}
                </Text>
              </View>
            )}
          </TouchableOpacity>
          
          <TouchableOpacity onPress={() => router.push('/cart')}>
            <Ionicons name="cart-outline" size={24} color="#333" />
          </TouchableOpacity>
        </View>
      </View>

      {/* 2. 투명 배경 모달을 활용한 알림 드롭다운 */}
      <Modal 
        visible={isDropdownVisible} 
        transparent={true} 
        animationType="fade"
        onRequestClose={handleCloseDropdown}
      >
        <TouchableOpacity 
          style={styles.modalOverlay} 
          activeOpacity={1} 
          onPress={handleCloseDropdown}
        >
          <TouchableOpacity activeOpacity={1} style={styles.dropdownContainer}>
            <View style={styles.dropdownHeader}>
              <Text fontWeight="bold" style={styles.dropdownHeaderText}>최근 알림</Text>
              <TouchableOpacity onPress={() => { handleCloseDropdown(); router.push('/notification' as any); }}>
                <Text style={styles.seeAllText}>전체보기</Text>
              </TouchableOpacity>
            </View>

            {isLoading ? (
              <ActivityIndicator style={{ padding: 20 }} color="#00A859" />
            ) : notifications.length === 0 ? (
              <Text style={styles.emptyText}>최근 알림이 없습니다.</Text>
            ) : (
              // 최대 3개 높이(maxHeight) 지정 및 스크롤 지원
              <FlatList
                data={notifications}
                keyExtractor={(item) => item.notificationId.toString()}
                renderItem={renderNotificationItem}
                style={{ maxHeight: 220 }} 
                showsVerticalScrollIndicator={true}
              />
            )}
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>

      <View style={styles.toggleContainer}>
        <TouchableOpacity 
          style={[styles.toggleBtn, activeTab === 'shop' && styles.toggleBtnActive]} 
          onPress={() => setActiveTab('shop')}
        >
          <Text style={[styles.toggleText, activeTab === 'shop' && styles.toggleTextActive]}>전체상점</Text>
        </TouchableOpacity>
        <TouchableOpacity 
          style={[styles.toggleBtn, activeTab === 'used' && styles.toggleBtnActive]} 
          onPress={() => setActiveTab('used')}
        >
          <Text style={[styles.toggleText, activeTab === 'used' && styles.toggleTextActive]}>중고거래</Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity style={styles.searchBar} onPress={onSearch}>
        <Text style={styles.searchText}>검색어를 입력해주세요</Text>
        <Ionicons name="search" size={20} color="#00A859" />
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  headerContainer: { paddingHorizontal: 20, paddingTop: 10, paddingBottom: 15, backgroundColor: '#fff', zIndex: 1 },
  headerTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 },
  locationSelector: { flexDirection: 'row', alignItems: 'center' },
  headerLocationText: { fontSize: 16, fontWeight: 'bold', color: '#333', marginHorizontal: 5 },
  headerIcons: { flexDirection: 'row', alignItems: 'center' },
  toggleContainer: { flexDirection: 'row', backgroundColor: '#F5F5F5', borderRadius: 25, padding: 4, marginBottom: 15, width: 200 },
  toggleBtn: { flex: 1, paddingVertical: 8, alignItems: 'center', borderRadius: 20 },
  toggleBtnActive: { backgroundColor: '#00A859' },
  toggleText: { fontSize: 14, color: '#888', fontWeight: '600' },
  toggleTextActive: { color: '#fff' },
  searchBar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#F5F5F5', paddingHorizontal: 15, paddingVertical: 12, borderRadius: 8 },
  searchText: { color: '#999', fontSize: 14 },

  // 알림 뱃지 스타일
  iconWrapper: { marginRight: 15, position: 'relative' },
  badge: { 
    position: 'absolute', top: -4, right: -4, 
    backgroundColor: '#FF5252', borderRadius: 10, minWidth: 18, height: 18, 
    justifyContent: 'center', alignItems: 'center', paddingHorizontal: 4, borderWidth: 1, borderColor: '#fff' 
  },
  badgeText: { color: '#fff', fontSize: 10 },

  // 알림 모달(드롭다운) 스타일
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.1)' },
  dropdownContainer: {
    position: 'absolute', top: 55, right: 20, // 헤더 아이콘들 바로 아래쪽에 오도록 위치 조정
    width: 280, backgroundColor: '#fff', borderRadius: 12, 
    shadowColor: '#000', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.15, shadowRadius: 10, elevation: 10,
    overflow: 'hidden'
  },
  dropdownHeader: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', 
    padding: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' 
  },
  dropdownHeaderText: { fontSize: 15, color: '#333' },
  seeAllText: { fontSize: 13, color: '#00A859' },
  emptyText: { padding: 20, textAlign: 'center', color: '#999', fontSize: 14 },
  
  // 개별 알림 아이템 스타일
  dropdownItem: { padding: 15, borderBottomWidth: 1, borderBottomColor: '#F8F9FA' },
  dropdownItemUnread: { backgroundColor: '#F9FCFA' }, // 안 읽은 알림은 연한 초록빛
  dropdownItemHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 4 },
  unreadDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#FF5252', marginRight: 6 },
  dropdownItemTitle: { flex: 1, fontSize: 14, color: '#333' },
  dropdownItemContent: { fontSize: 12, color: '#666', marginBottom: 4 },
  dropdownItemTime: { fontSize: 11, color: '#AAA' }
});