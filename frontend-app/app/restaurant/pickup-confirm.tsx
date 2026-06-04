import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Image, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { shopApi } from '../../api/shop';

// 💡 나중에 메인 브랜치와 합쳐지면 아래 주석을 풀어주세요!
// import { userApi } from '../../api/user'; 

export default function PickupConfirmScreen() {
  const router = useRouter();
  const { storeId, month, date, time, request } = useLocalSearchParams();
  const [shopInfo, setShopInfo] = useState<any>(null);

  // 💡 가짜 데이터: 나중에 userApi 연동 후 삭제
  const [userInfo, setUserInfo] = useState({
    name: '김수빈 (임시)',
    phone: '010-1234-1234'
  });

  useEffect(() => {
    const fetchShop = async () => {
      try {
        const data = await shopApi.getShopDetail(Number(storeId));
        setShopInfo(data);
      } catch (e) {
        console.error(e);
      }
    };
    
    // 💡 나중에 백엔드 API 연동 시 주석을 풀고 사용하세요.
    /*
    const fetchUserInfo = async () => {
      try {
        const myInfo = await userApi.getMyInfo();
        setUserInfo({ name: myInfo.name, phone: myInfo.phone || '전화번호 없음' });
      } catch (error) {
        console.error("내 정보 불러오기 실패", error);
      }
    };
    fetchUserInfo();
    */

    if (storeId) fetchShop();
  }, [storeId]);

  const handleFinalPickup = async () => {
    // 🚀 나중에 백엔드 API가 나오면 이 안에 코드를 넣으세요!
    /*
    try {
      const response = await client.post('/pickups', {
        storeId: Number(storeId),
        pickupDate: `2026-${month}-${date}`, 
        pickupTime: time,
        requestMessage: request
      });
      if (response.data.success) {
        router.push('/restaurant/pickup-success' as any);
      }
    } catch (error) {
      Alert.alert('접수 실패', '픽업 접수에 실패했습니다.');
    }
    */

    // 지금은 API가 없으니 버튼을 누르면 바로 성공 화면으로 넘어갑니다.
    router.push('/restaurant/pickup-success' as any);
  };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={{ padding: 5 }}>
          <Ionicons name="chevron-back" size={24} color="#fff" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>픽업 주문 확인</Text>
        <View style={{ width: 34 }} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>주문자 정보</Text>
          <View style={styles.card}>
            <View style={styles.row}>
              <Text fontWeight="bold" style={styles.label}>주문자</Text>
              <Text style={styles.value}>{userInfo.name}  {userInfo.phone}</Text>
            </View>
            <View style={[styles.row, { marginTop: 10 }]}>
              <Text fontWeight="bold" style={styles.label}>픽업 일정</Text>
              <Text style={styles.value}>{month}월 {date}일 {time}</Text>
            </View>
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>픽업 매장</Text>
          <View style={styles.card}>
            <Text fontWeight="bold" style={styles.shopName}>{shopInfo?.name || '상점 이름'}</Text>
            <View style={styles.shopInfoBox}>
              <Image 
                source={{ uri: shopInfo?.images?.[0]?.imageUrl || 'https://via.placeholder.com/100' }} 
                style={styles.shopImage} 
              />
              <View style={styles.shopDetails}>
                <Text style={styles.shopText} numberOfLines={1}>{shopInfo?.address || '주소 정보'}</Text>
                <Text style={styles.shopText}>픽업 일정  {month}월 {date}일 {time}</Text>
              </View>
            </View>
          </View>
        </View>

        <View style={styles.noticeBox}>
          <Text style={styles.noticeTitle}>픽업 주의사항</Text>
          <Text style={styles.noticeText}>
            • 지정된 픽업 시간에 늦지 않게 매장을 방문해 주세요.{'\n'}
            • 매장 상황에 따라 상품 준비 시간이 조금 달라질 수 있습니다.{'\n'}
            • 픽업 시 주문 내역 화면을 사장님께 보여주세요.
          </Text>
        </View>
      </ScrollView>

      <View style={styles.bottomBar}>
        <TouchableOpacity style={styles.submitBtn} onPress={handleFinalPickup}>
          <Text fontWeight="bold" style={styles.submitBtnText}>픽업 접수하기</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8F9FA' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 15, paddingVertical: 15, backgroundColor: '#00A859' },
  headerTitle: { fontSize: 16, color: '#fff' },
  scrollContent: { padding: 20, paddingBottom: 100 },
  section: { marginBottom: 25 },
  sectionTitle: { fontSize: 14, color: '#666', marginBottom: 10 },
  card: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#EBEBEB', borderRadius: 8, padding: 20 },
  row: { flexDirection: 'row', alignItems: 'center' },
  label: { width: 70, fontSize: 14, color: '#333' },
  value: { fontSize: 14, color: '#333', flex: 1 },
  shopName: { fontSize: 16, color: '#333', marginBottom: 15 },
  shopInfoBox: { flexDirection: 'row' },
  shopImage: { width: 70, height: 70, borderRadius: 4, backgroundColor: '#eee', marginRight: 15 },
  shopDetails: { flex: 1, justifyContent: 'center', gap: 6 },
  shopText: { fontSize: 13, color: '#666' },
  noticeBox: { backgroundColor: '#F4F5F7', padding: 20, borderRadius: 8 },
  noticeTitle: { fontSize: 13, color: '#888', marginBottom: 8, fontWeight: 'bold' },
  noticeText: { fontSize: 12, color: '#888', lineHeight: 20 },
  bottomBar: { position: 'absolute', bottom: 0, width: '100%', backgroundColor: '#F8F9FA', padding: 20, paddingTop: 10 },
  submitBtn: { backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  submitBtnText: { color: '#fff', fontSize: 16 }
});