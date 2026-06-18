import React, { useState, useCallback } from 'react';
import { View, StyleSheet, FlatList, TouchableOpacity, TextInput, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText';
import { chatApi } from '../../api/chat';

export default function ChatListScreen() {
  const router = useRouter();
  const [rooms, setRooms] = useState<any[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  
  const [activeCategory, setActiveCategory] = useState('동네상점'); 
  const [activeType, setActiveType] = useState('전체'); 

  // ✨ 화면에 포커스가 올 때마다(진입 시) 최신 채팅방 목록을 불러옵니다.
  useFocusEffect(
    useCallback(() => {
      const fetchRooms = async () => {
        try {
          setIsLoading(true);
          const res = await chatApi.getRooms();
          // 백엔드 응답 구조(Data, Content 등)에 맞게 안전하게 추출
          const realData = res?.data?.content || res?.data || res || [];
          setRooms(realData);
        } catch (error) {
          console.error('채팅 목록 로딩 실패', error);
        } finally {
          setIsLoading(false);
        }
      };
      fetchRooms();
    }, [])
  );

  const FilterChip = ({ title, isActive, onPress }: any) => (
    <TouchableOpacity 
      style={[styles.chip, isActive ? styles.activeChip : styles.inactiveChip]} 
      onPress={onPress}
    >
      <Text style={[styles.chipText, isActive ? styles.activeChipText : styles.inactiveChipText]}>{title}</Text>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <Text fontWeight="bold" style={styles.headerTitle}>이음톡</Text>
      </View>

      <View style={styles.searchContainer}>
        <View style={styles.searchBox}>
          <TextInput 
            style={styles.searchInput} 
            placeholder="채팅 검색" 
            value={searchQuery}
            onChangeText={setSearchQuery}
          />
          <Ionicons name="search" size={20} color="#999" />
        </View>
      </View>

      <View style={styles.filterSection}>
        <View style={styles.filterRow}>
          <FilterChip title="동네상점" isActive={activeCategory === '동네상점'} onPress={() => setActiveCategory('동네상점')} />
          <FilterChip title="중고거래" isActive={activeCategory === '중고거래'} onPress={() => setActiveCategory('중고거래')} />
        </View>
        <View style={styles.filterRow}>
          <FilterChip title="전체" isActive={activeType === '전체'} onPress={() => setActiveType('전체')} />
          <FilterChip title="단체채팅" isActive={activeType === '단체채팅'} onPress={() => setActiveType('단체채팅')} />
          <FilterChip title="개인채팅" isActive={activeType === '개인채팅'} onPress={() => setActiveType('개인채팅')} />
        </View>
      </View>

      {/* 로딩 스피너 추가 */}
      {isLoading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color="#1B854A" />
        </View>
      ) : (
        <FlatList
          data={rooms}
          // 백엔드 키값(roomId) 방어 코드 적용
          keyExtractor={(item) => (item.roomId || item.id).toString()}
          renderItem={({ item }) => (
            <TouchableOpacity 
              style={styles.roomItem}
              onPress={() => router.push(`/chat/${item.roomId || item.id}` as any)}
            >
              <View style={styles.avatarContainer}>
                <View style={styles.avatarPlaceholder}>
                  <Ionicons name="image-outline" size={24} color="#CCC" />
                </View>
                {item.unreadCount > 0 && (
                  <View style={styles.badge}>
                    <Text style={styles.badgeText}>{item.unreadCount}</Text>
                  </View>
                )}
              </View>
              <View style={styles.roomInfo}>
                <View style={styles.roomHeader}>
                  {/* 방 이름 매핑 */}
                  <Text fontWeight="bold" style={styles.roomTitle}>{item.roomName || item.title || '채팅방'}</Text>
                  <Text style={styles.timeText}>{item.lastMessageTime || item.time || ''}</Text>
                </View>
                <Text style={styles.lastMessage} numberOfLines={1}>{item.lastMessage}</Text>
              </View>
            </TouchableOpacity>
          )}
        />
      )}
    </SafeAreaView>
  );
}

// 스타일은 기존과 동일합니다.
const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { padding: 20 },
  headerTitle: { fontSize: 24, color: '#333' },
  searchContainer: { paddingHorizontal: 20, paddingBottom: 15 },
  searchBox: { flexDirection: 'row', backgroundColor: '#F5F6F8', borderRadius: 8, paddingHorizontal: 15, paddingVertical: 10, alignItems: 'center' },
  searchInput: { flex: 1, fontSize: 15 },
  filterSection: { paddingHorizontal: 20, paddingBottom: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  filterRow: { flexDirection: 'row', marginBottom: 10, gap: 8 },
  chip: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20 },
  activeChip: { backgroundColor: '#1B854A' },
  inactiveChip: { backgroundColor: '#F5F6F8' },
  chipText: { fontSize: 14, fontWeight: '600' },
  activeChipText: { color: '#fff' },
  inactiveChipText: { color: '#666' },
  roomItem: { flexDirection: 'row', padding: 20, borderBottomWidth: 1, borderBottomColor: '#F9F9F9' },
  avatarContainer: { marginRight: 15 },
  avatarPlaceholder: { width: 50, height: 50, borderRadius: 25, backgroundColor: '#F0F0F0', justifyContent: 'center', alignItems: 'center', borderWidth: 1, borderColor: '#DDD' },
  badge: { position: 'absolute', right: -5, top: -5, backgroundColor: '#1B854A', minWidth: 20, height: 20, borderRadius: 10, justifyContent: 'center', alignItems: 'center', borderWidth: 2, borderColor: '#fff', paddingHorizontal: 4 },
  badgeText: { color: '#fff', fontSize: 10, fontWeight: 'bold' },
  roomInfo: { flex: 1, justifyContent: 'center' },
  roomHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 },
  roomTitle: { fontSize: 16, color: '#333' },
  timeText: { fontSize: 12, color: '#999' },
  lastMessage: { fontSize: 14, color: '#666' }
});