import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, TextInput, ActivityIndicator, Alert } from 'react-native';
import { Text } from '../../components/CustomText';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { shopApi } from '../../api/shop';

export default function ReservationInputScreen() {
  const router = useRouter();
  const { storeId } = useLocalSearchParams();

  const [shopInfo, setShopInfo] = useState<any>(null);
  const [selectedDate, setSelectedDate] = useState<any>(null); // 💡 날짜 객체를 통째로 저장
  const [selectedTime, setSelectedTime] = useState<string | null>(null);
  const [peopleCount, setPeopleCount] = useState<string>('');
  const [requestText, setRequestText] = useState<string>('');

  // ✨ 오늘부터 7일간의 날짜를 동적으로 생성하는 함수
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
        fullDate: nextDate.toISOString().split('T')[0] // 고유 ID용 (YYYY-MM-DD)
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

  const isFormValid = selectedDate !== null && selectedTime !== null && peopleCount.trim() !== '';

  const handleNext = () => {
    if (!isFormValid) return;
    
    // ✨ 다음 화면으로 '월', '일' 실제 데이터 넘기기
    router.push({
      pathname: '/restaurant/reservation-confirm' as any,
      params: { 
        storeId, 
        month: selectedDate.month, // 동적 월
        date: selectedDate.date,   // 동적 일
        time: selectedTime, 
        people: peopleCount, 
        request: requestText 
      }
    });
  };

  if (!shopInfo) {
    return <View style={styles.center}><ActivityIndicator size="large" color="#00A859" /></View>;
  }

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={{ padding: 5 }}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>방문 예약</Text>
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
            <Text fontWeight="bold" style={styles.sectionTitle}>방문 날짜</Text>
          </View>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.dateScroll}>
            {dateList.map((item, index) => {
              const isActive = selectedDate?.fullDate === item.fullDate;
              return (
                <TouchableOpacity 
                  key={index} 
                  style={[styles.dateBox, isActive && styles.dateBoxActive]}
                  onPress={() => setSelectedDate(item)} // ✨ 전체 객체 저장
                >
                  <Text style={[styles.dateDay, isActive && styles.textActive]}>{item.day}</Text>
                  <Text fontWeight="bold" style={[styles.dateNum, isActive && styles.textActive]}>{item.date}</Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        </View>

        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Ionicons name="time-outline" size={18} color="#333" />
            <Text fontWeight="bold" style={styles.sectionTitle}>방문 시간</Text>
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
          <Text fontWeight="bold" style={styles.sectionTitleBasic}>인원</Text>
          <TextInput 
            style={styles.input} 
            placeholder="방문 인원을 입력해주세요 (예: 2)"
            keyboardType="number-pad"
            value={peopleCount}
            onChangeText={setPeopleCount}
          />
        </View>

        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitleBasic}>요청사항 (선택)</Text>
          <TextInput 
            style={styles.textArea} 
            placeholder="요청사항을 입력하세요"
            multiline
            textAlignVertical="top"
            value={requestText}
            onChangeText={setRequestText}
          />
        </View>
      </ScrollView>

      <View style={styles.bottomBar}>
        <TouchableOpacity 
          style={[styles.submitBtn, isFormValid && styles.submitBtnActive]} 
          disabled={!isFormValid}
          onPress={handleNext}
        >
          <Text fontWeight="bold" style={styles.submitBtnText}>예약하기</Text>
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
  input: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 4, padding: 15, fontSize: 15 },
  textArea: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 4, padding: 15, fontSize: 15, height: 120 },
  bottomBar: { position: 'absolute', bottom: 0, width: '100%', backgroundColor: '#F8F9FA', padding: 20, paddingTop: 10 },
  submitBtn: { backgroundColor: '#D5D8DC', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  submitBtnActive: { backgroundColor: '#00A859' },
  submitBtnText: { color: '#fff', fontSize: 16 }
});