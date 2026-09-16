import React, { useState, useCallback } from 'react';
import { 
  View, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator, Alert 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';
import { Text } from '../../components/CustomText'; // 커스텀 텍스트 경로 확인해주세요!
import { inquiryApi } from '../../api/inquiry'; 

export default function MyInquiriesScreen() {
  const router = useRouter();
  
  const [inquiries, setInquiries] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // 화면에 들어올 때마다 최신 내역을 조회합니다.
  useFocusEffect(
    useCallback(() => {
      fetchMyInquiries();
    }, [])
  );

  const fetchMyInquiries = async () => {
    try {
      setIsLoading(true);
      const data = await inquiryApi.getMyInquiries(0, 50); // 페이지 0, 사이즈 50 (필요시 수정)
      setInquiries(data);
    } catch (error) {
      console.error('내 문의 내역 로딩 실패:', error);
      Alert.alert('오류', '문의 내역을 불러오는데 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const getStatusInfo = (status: string) => {
    // 백엔드의 status 값에 따라 색상과 텍스트를 다르게 렌더링
    if (status === 'ANSWERED' || status === 'COMPLETED') {
      return { text: '답변완료', color: '#00A859', bgColor: '#E8F5E9' };
    }
    // 기본값은 PENDING (답변 대기)
    return { text: '답변대기', color: '#FF9800', bgColor: '#FFF3E0' };
  };

  const renderInquiryItem = ({ item }: { item: any }) => {
    const statusInfo = getStatusInfo(item.status);
    const dateText = item.createdAt ? item.createdAt.replace('T', ' ').substring(0, 10) : '';

    return (
      <TouchableOpacity 
        style={styles.cardContainer}
        // 나중에 만들 상세 화면([id].tsx)으로 이동
        onPress={() => router.push(`/inquiry/${item.inquiryId}` as any)}
      >
        <View style={styles.cardHeader}>
          <Text style={styles.dateText}>{dateText}</Text>
          <View style={[styles.statusBadge, { backgroundColor: statusInfo.bgColor }]}>
            <Text style={[styles.statusText, { color: statusInfo.color }]}>{statusInfo.text}</Text>
          </View>
        </View>

        <View style={styles.cardBody}>
          <Text style={styles.categoryText}>
            [{item.targetType === 'ADMIN' ? '고객센터' : '상점문의'}] {item.storeName ? item.storeName : ''}
          </Text>
          <Text fontWeight="bold" style={styles.titleText} numberOfLines={1}>
            {item.secret && <Ionicons name="lock-closed" size={14} color="#888" style={{ marginRight: 4 }} />}
            {item.secret ? ' 비밀글입니다. ' : ' '}{item.title}
          </Text>
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>내 문의 내역</Text>
        <View style={{ width: 24 }} /> 
      </View>

      {/* 목록 리스트 */}
      {isLoading ? (
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color="#00A859" />
        </View>
      ) : inquiries.length === 0 ? (
        <View style={styles.centerContainer}>
          <Ionicons name="chatbubbles-outline" size={60} color="#DDD" style={{ marginBottom: 15 }} />
          <Text style={styles.emptyText}>작성하신 문의 내역이 없습니다.</Text>
        </View>
      ) : (
        <FlatList
          data={inquiries}
          keyExtractor={(item) => item.inquiryId.toString()}
          renderItem={renderInquiryItem}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8F9FA' },
  header: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', 
    paddingHorizontal: 20, paddingVertical: 15, backgroundColor: '#fff',
    borderBottomWidth: 1, borderBottomColor: '#F0F0F0'
  },
  backButton: { padding: 4, marginLeft: -4 },
  headerTitle: { fontSize: 18, color: '#333' },
  
  centerContainer: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#F8F9FA' },
  emptyText: { fontSize: 16, color: '#666', fontWeight: 'bold' },
  
  listContainer: { padding: 20 },
  cardContainer: { 
    backgroundColor: '#fff', borderRadius: 12, padding: 18, marginBottom: 15,
    shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.05, shadowRadius: 5, elevation: 3
  },
  cardHeader: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', 
    marginBottom: 12, borderBottomWidth: 1, borderBottomColor: '#F0F0F0', paddingBottom: 10 
  },
  dateText: { fontSize: 13, color: '#888' },
  statusBadge: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 6 },
  statusText: { fontSize: 12, fontWeight: 'bold' },
  
  cardBody: { justifyContent: 'center' },
  categoryText: { fontSize: 13, color: '#666', marginBottom: 6 },
  titleText: { fontSize: 16, color: '#333', lineHeight: 22 },
});