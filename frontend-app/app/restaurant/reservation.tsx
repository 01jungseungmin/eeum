import React, { useState, useEffect } from 'react';
import { 
  View, StyleSheet, ScrollView, TouchableOpacity, 
  TextInput, ActivityIndicator, Alert 
} from 'react-native';
import { Text } from '../../components/CustomText';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { shopApi } from '../../api/shop';
import { reservationApi } from '../../api/reservation';
import { getApiErrorMessage } from '../../utils/apiError';

/**
 * 화면에 보이는 그 날짜를 그대로 YYYY-MM-DD 로 만든다.
 *
 * toISOString()을 쓰면 안 된다 — UTC 기준이라 한국(UTC+9)에서는 오전 9시 이전에
 * 날짜가 하루 뒤로 밀린다. 버튼 라벨은 getDate()(로컬)로 그리는데 전송값만 밀려서,
 * 아침에 들어온 사용자는 "오늘" 버튼을 눌렀는데 어제가 전송돼 예약이 통째로 막혔다.
 */
const toLocalDateString = (date: Date): string => {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
};

export default function ReservationInputScreen() {
  const router = useRouter();
  const { storeId } = useLocalSearchParams();

  const [shopInfo, setShopInfo] = useState<any>(null);
  const [selectedDate, setSelectedDate] = useState<any>(null);
  const [peopleCount, setPeopleCount] = useState<string>('');
  const [selectedTime, setSelectedTime] = useState<string | null>(null);
  const [requestText, setRequestText] = useState<string>('');

  // 동적 시간대 로딩을 위한 State 추가
  const [timeSlots, setTimeSlots] = useState<any[]>([]);
  const [isLoadingSlots, setIsLoadingSlots] = useState(false);
  // 시간대를 못 불러온 사유. 서버가 알려준 문구를 그대로 담는다.
  const [slotError, setSlotError] = useState<string | null>(null);

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
        // API 요청용 포맷 (YYYY-MM-DD)
        fullDate: toLocalDateString(nextDate)
      });
    }
    return dates;
  };

  const dateList = generateDates();

  // 1. 상점 정보 불러오기
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

  // 2. 날짜나 인원이 변경되면 백엔드에서 예약 가능 시간대를 불러오는 로직 (디바운스 적용)
  useEffect(() => {
    // 날짜와 인원이 모두 입력되어야만 정확한 빈 테이블 조회가 가능합니다.
    if (!storeId || !selectedDate || !peopleCount.trim()) {
      setTimeSlots([]);
      setSelectedTime(null);
      setSlotError(null);
      return;
    }

    const fetchTimeSlots = async () => {
      try {
        setIsLoadingSlots(true);
        setSlotError(null);
        const data = await reservationApi.getAvailableTimeSlots(Number(storeId), {
          date: selectedDate.fullDate,
          partySize: Number(peopleCount)
        });
        setTimeSlots(data || []);
        setSelectedTime(null); // 날짜/인원이 바뀌면 기존에 선택한 시간 초기화
      } catch (error) {
        console.error('시간대 로딩 실패', error);
        setTimeSlots([]);
        // 서버는 사유를 정확히 알려준다 — 예약 설정 없음, 휴무일, 당일예약 불가 등.
        // 이걸 삼키면 화면이 "날짜와 인원을 입력하세요"라고만 말해서, 이미 입력한
        // 사용자에게는 거짓말이 된다.
        setSlotError(getApiErrorMessage(error, '예약 가능한 시간을 불러오지 못했어요.'));
      } finally {
        setIsLoadingSlots(false);
      }
    };

    // 사용자가 인원 입력을 마칠 때까지 잠깐 기다렸다가 API를 호출합니다 (API 과부하 방지)
    const timeoutId = setTimeout(() => {
      fetchTimeSlots();
    }, 500);

    return () => clearTimeout(timeoutId);
  }, [storeId, selectedDate, peopleCount]);

  const isFormValid = selectedDate !== null && selectedTime !== null && peopleCount.trim() !== '';

  const handleNext = () => {
    if (!isFormValid) return;
    
    router.push({
      pathname: '/restaurant/reservation-confirm' as any,
      params: { 
        storeId, 
        month: selectedDate.month, 
        date: selectedDate.date, 
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

        {/* 1. 방문 날짜 선택 */}
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
                  onPress={() => setSelectedDate(item)} 
                >
                  <Text style={[styles.dateDay, isActive && styles.textActive]}>{item.day}</Text>
                  <Text fontWeight="bold" style={[styles.dateNum, isActive && styles.textActive]}>{item.date}</Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        </View>

        {/* 2. 인원수 입력 (시간대보다 위로 올렸습니다. 날짜+인원이 있어야 시간대를 불러옵니다) */}
        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Ionicons name="people-outline" size={18} color="#333" />
            <Text fontWeight="bold" style={styles.sectionTitle}>방문 인원</Text>
          </View>
          <TextInput 
            style={styles.input} 
            placeholder="방문 인원을 숫자로 입력해주세요 (예: 2)"
            keyboardType="number-pad"
            value={peopleCount}
            onChangeText={setPeopleCount}
          />
        </View>

        {/* 3. 방문 시간 선택 (동적 렌더링) */}
        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Ionicons name="time-outline" size={18} color="#333" />
            <Text fontWeight="bold" style={styles.sectionTitle}>방문 시간</Text>
          </View>

          {isLoadingSlots ? (
            <View style={styles.emptySlotBox}>
              <ActivityIndicator size="small" color="#00A859" />
              <Text style={styles.emptySlotText}>예약 가능한 시간을 찾고 있어요...</Text>
            </View>
          ) : slotError ? (
            // 서버가 막은 경우 (예약 설정 없음, 휴무일, 당일예약 불가 등)
            <View style={styles.emptySlotBox}>
              <Ionicons name="alert-circle-outline" size={24} color="#FF8A65" style={{ marginBottom: 8 }} />
              <Text style={styles.emptySlotText}>{slotError}</Text>
            </View>
          ) : !selectedDate || !peopleCount.trim() ? (
            // 아직 입력이 덜 된 경우 — 원래 이 안내는 여기서만 맞다
            <View style={styles.emptySlotBox}>
              <Ionicons name="information-circle-outline" size={24} color="#999" style={{ marginBottom: 8 }} />
              <Text style={styles.emptySlotText}>
                방문 날짜와 인원을 먼저 입력하시면{'\n'}예약 가능한 시간대가 표시됩니다.
              </Text>
            </View>
          ) : timeSlots.length === 0 ? (
            // 입력은 끝났는데 슬롯이 0개 — 그날 운영 시간대가 없다는 뜻이다
            <View style={styles.emptySlotBox}>
              <Ionicons name="information-circle-outline" size={24} color="#999" style={{ marginBottom: 8 }} />
              <Text style={styles.emptySlotText}>
                이 날짜에는 예약할 수 있는 시간대가 없어요.{'\n'}다른 날짜를 선택해 주세요.
              </Text>
            </View>
          ) : timeSlots.every((slot) => !slot.available) ? (
            // 시간대는 있는데 전부 불가 — 대개 인원수에 맞는 테이블이 없는 경우다.
            // 시간 칸을 전부 회색으로만 보여주면 왜 안 되는지 알 수 없다.
            <View style={styles.emptySlotBox}>
              <Ionicons name="people-outline" size={24} color="#999" style={{ marginBottom: 8 }} />
              <Text style={styles.emptySlotText}>
                {peopleCount}명이 앉을 수 있는 자리가 없어요.{'\n'}인원을 줄이거나 다른 날짜를 선택해 주세요.
              </Text>
            </View>
          ) : (
            <View style={styles.timeGrid}>
              {timeSlots.map((slot, index) => {
                // API에서 넘어오는 "10:00:00" 포맷을 UI용 "10:00"으로 자르기
                const timeStr = slot.time.substring(0, 5); 
                const isAvailable = slot.available;
                const isActive = selectedTime === timeStr;
                
                return (
                  <TouchableOpacity 
                    key={index} 
                    style={[
                      styles.timeBox, 
                      isActive && styles.timeBoxActive,
                      !isAvailable && styles.timeBoxDisabled // 예약 꽉 찬 시간 스타일 적용
                    ]}
                    disabled={!isAvailable} // 꽉 찬 시간은 터치 불가
                    onPress={() => setSelectedTime(timeStr)}
                  >
                    <Text style={[
                      styles.timeText, 
                      isActive && styles.textActive,
                      !isAvailable && styles.textDisabled
                    ]}>
                      {timeStr}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          )}
        </View>

        {/* 4. 요청사항 입력 */}
        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Ionicons name="chatbubble-ellipses-outline" size={18} color="#333" />
            <Text fontWeight="bold" style={styles.sectionTitle}>요청사항 (선택)</Text>
          </View>
          <TextInput 
            style={styles.textArea} 
            placeholder="알레르기 정보나 특별한 요청사항을 입력하세요"
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
          <Text fontWeight="bold" style={styles.submitBtnText}>예약 계속하기</Text>
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
  sectionHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
  sectionTitle: { fontSize: 15, color: '#333', marginLeft: 6 },
  
  dateScroll: { flexDirection: 'row' },
  dateBox: { width: 60, height: 70, borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 8, justifyContent: 'center', alignItems: 'center', marginRight: 10, backgroundColor: '#fff' },
  dateBoxActive: { borderColor: '#00A859', backgroundColor: '#F0FFF4' },
  dateDay: { fontSize: 13, color: '#666', marginBottom: 4 },
  dateNum: { fontSize: 18, color: '#333' },
  textActive: { color: '#00A859' },

  emptySlotBox: { backgroundColor: '#F4F5F7', padding: 30, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  emptySlotText: { color: '#888', fontSize: 13, textAlign: 'center', lineHeight: 20, marginTop: 10 },

  timeGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  timeBox: { width: '23%', paddingVertical: 12, borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 4, alignItems: 'center', marginBottom: 10, backgroundColor: '#fff' },
  timeBoxActive: { borderColor: '#00A859', backgroundColor: '#F0FFF4' },
  timeBoxDisabled: { backgroundColor: '#F9F9F9', borderColor: '#EAEAEA' }, // 비활성화 박스 스타일
  timeText: { fontSize: 14, color: '#555' },
  textDisabled: { color: '#CCC' }, // 비활성화 텍스트 색상

  input: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 4, padding: 15, fontSize: 15 },
  textArea: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 4, padding: 15, fontSize: 15, height: 120 },
  
  bottomBar: { position: 'absolute', bottom: 0, width: '100%', backgroundColor: '#F8F9FA', padding: 20, paddingTop: 10 },
  submitBtn: { backgroundColor: '#D5D8DC', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  submitBtnActive: { backgroundColor: '#00A859' },
  submitBtnText: { color: '#fff', fontSize: 16 }
});