import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, TextInput, ActivityIndicator, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { shopApi } from '../../api/shop';

export default function PickupInputScreen() {
  const router = useRouter();
  const { storeId } = useLocalSearchParams();

  const [shopInfo, setShopInfo] = useState<any>(null);
  const [selectedDate, setSelectedDate] = useState<any>(null);
  const [selectedTime, setSelectedTime] = useState<string | null>(null);
  const [requestText, setRequestText] = useState<string>('');

  // 오늘부터 7일간의 날짜를 동적으로 생성
  const generateDates = () => {
    const dates = [];
    const today = new Date();
    const dayNames = ['일', '월', '화', '수', '목', '금', '토'];
    
    for (let i = 0; i < 7; i++) {
      const nextDate = new Date(today);
      nextDate.setDate(today.getDate() + i);
      dates.push({
        day: dayNames[nextDate.getDay()],
        date: nextDate.getDate(),
        month: nextDate.getMonth() + 1,
        fullDate: nextDate.toISOString().split('T')[0]
      });
    }
    return dates;
  };

  const dateList = generateDates();

  const timeList = [
    '10:00', '10:30', '11:00', '11:30',
    '13:00', '13:30', '14:00', '14:30',
    '15:00', '15:30', '16:00', '16:30',
    '17:00', '17:30', '18:00', '18:30'
  ];

  useEffect(() => {
    const fetchShop = async () => {
      try {
        const data = await shopApi.getShopDetail(Number(storeId));
        setShopInfo(data);
      } catch (e) {
        Alert.alert('오류', '상점 정보를 불러올 수 없습니다.');
        router.back();
      }
    };
    if (storeId) fetchShop();
  }, [storeId]);

  // 픽업은 날짜와 시간만 선택하면 유효함
  const isFormValid = selectedDate !== null && selectedTime !== null;

  const handleNext = () => {
    if (!isFormValid) return;
    
    // 다음 '픽업 확인' 화면으로 데이터 넘기기
    router.push({
      pathname: '/restaurant/pickup-confirm' as any,
      params: { 
        storeId, 
        month: selectedDate.month,
        date: selectedDate.date,
        time: selectedTime, 
        request: requestText 
      }
    });
  };

  if (!shopInfo) {
    return <View style={styles.center}><ActivityIndicator size="large" color="#00A859" /></View>;
  }

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={{ padding: 5 }}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>픽업 주문</Text>
        <View style={{ width: 34 }} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
        <View style={styles.infoBox}>
          <Text fontWeight="bold" style={styles.shopName}>{shopInfo.name}</Text>
          <Text style={styles.shopAddress}>{shopInfo.address}</Text>
        </View>

        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Ionicons name="calendar-outline" size={18} color="#333" />
            <Text fontWeight="bold" style={styles.sectionTitle}>픽업 날짜</Text>
          </View>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.dateScroll}>
            {dateList.map((item, index) => {
              const isActive = selectedDate?.fullDate === item.fullDate;
              return (
                <TouchableOpacity 
                  key={index} 
                  style={[styles.dateBox, isActive && styles.dateBoxActive]}
                  onPress={() => setSelectedDate(item)}
                >
                  <Text style={[styles.dateDay, isActive && styles.textActive]}>{index === 0 ? '오늘' : item.day}</Text>
                  <Text fontWeight="bold" style={[styles.dateNum, isActive && styles.textActive]}>{item.date}</Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        </View>

        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Ionicons name="time-outline" size={18} color="#333" />
            <Text fontWeight="bold" style={styles.sectionTitle}>픽업 시간</Text>
          </View>
          <View style={styles.timeGrid}>
            {timeList.map((time, index) => {
              const isActive = selectedTime === time;
              return (
                <TouchableOpacity 
                  key={index} 
                  style={[styles.timeBox, isActive && styles.timeBoxActive]}
                  onPress={() => setSelectedTime(time)}
                >
                  <Text style={[styles.timeText, isActive && styles.textActive]}>{time}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </View>

        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitleBasic}>요청사항 (선택)</Text>
          <TextInput 
            style={styles.textArea} 
            placeholder="사장님께 전달할 픽업 요청사항을 입력하세요"
            multiline
            textAlignVertical="top"
            value={requestText}
            onChangeText={setRequestText}
          />
        </View>
      </ScrollView>

      {/* 하단 버튼 */}
      <View style={styles.bottomBar}>
        <TouchableOpacity 
          style={[styles.submitBtn, isFormValid && styles.submitBtnActive]} 
          disabled={!isFormValid}
          onPress={handleNext}
        >
          <Text fontWeight="bold" style={styles.submitBtnText}>다음으로</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8F9FA' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 15, paddingVertical: 15, backgroundColor: '#fff', borderBottomWidth: 1, borderBottomColor: '#eee' },
  headerTitle: { fontSize: 16, color: '#333' },
  scrollContent: { padding: 20, paddingBottom: 100 },
  infoBox: { backgroundColor: '#F4F5F7', padding: 20, borderRadius: 8, marginBottom: 25 },
  shopName: { fontSize: 18, color: '#333', marginBottom: 6 },
  shopAddress: { fontSize: 13, color: '#666' },
  section: { marginBottom: 30 },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 15 },
  sectionTitle: { fontSize: 15, color: '#333', marginLeft: 6 },
  sectionTitleBasic: { fontSize: 14, color: '#333', marginBottom: 10 },
  dateScroll: { flexDirection: 'row' },
  dateBox: { width: 60, height: 70, borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 8, justifyContent: 'center', alignItems: 'center', marginRight: 10, backgroundColor: '#fff' },
  dateBoxActive: { borderColor: '#00A859', backgroundColor: '#F0FFF4' },
  dateDay: { fontSize: 13, color: '#666', marginBottom: 4 },
  dateNum: { fontSize: 18, color: '#333' },
  textActive: { color: '#00A859' },
  timeGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  timeBox: { width: '23%', paddingVertical: 12, borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 4, alignItems: 'center', marginBottom: 10, backgroundColor: '#fff' },
  timeBoxActive: { borderColor: '#00A859', backgroundColor: '#F0FFF4' },
  timeText: { fontSize: 14, color: '#555' },
  textArea: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 4, padding: 15, fontSize: 15, height: 120 },
  bottomBar: { position: 'absolute', bottom: 0, width: '100%', backgroundColor: '#F8F9FA', padding: 20, paddingTop: 10 },
  submitBtn: { backgroundColor: '#D5D8DC', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  submitBtnActive: { backgroundColor: '#00A859' },
  submitBtnText: { color: '#fff', fontSize: 16 }
});