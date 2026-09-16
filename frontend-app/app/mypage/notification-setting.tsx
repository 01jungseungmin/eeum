import React, { useState, useEffect } from 'react';
import { View, StyleSheet, TouchableOpacity, Switch, ActivityIndicator, Alert, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../../components/CustomText';
import { notificationApi } from '../../api/notification';

export default function NotificationSettingsScreen() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  // 백엔드 Swagger 명세에 맞춘 전체 설정 상태들 (화면에서 뺀 항목도 서버 보존을 위해 남겨둠)
  const [settings, setSettings] = useState({
    allEnabled: true,
    chatEnabled: true,
    communityEnabled: true,
    storeReviewEnabled: true,
    usedProductEnabled: false,
    stockEnabled: true,
    settlementEnabled: true,
    marketingEnabled: false,
    dndEnabled: true,
    dndStartTime: "22:00",
    dndEndTime: "08:00",
    orderEmailEnabled: true,
    chatEmailEnabled: false,
    reviewEmailEnabled: true,
    reservationEmailEnabled: false,
    stockEmailEnabled: true,
    settlementEmailEnabled: true,
    orderSoundEnabled: true,
    chatSoundEnabled: true,
    reviewSoundEnabled: false,
    reservationSoundEnabled: true,
    stockSoundEnabled: false,
    settlementSoundEnabled: false
  });

  // 초기 설정값 불러오기
  useEffect(() => {
    const fetchSettings = async () => {
      try {
        const res = await notificationApi.getSettings();
        if (res.data) {
          // 기존 초기값에 서버에서 받아온 값 덮어쓰기
          setSettings(prev => ({ ...prev, ...res.data }));
        }
      } catch (error) {
        Alert.alert('오류', '알림 설정을 불러오지 못했습니다.');
      } finally {
        setIsLoading(false);
      }
    };
    fetchSettings();
  }, []);

  // 토글 스위치 변경 핸들러
  const toggleSwitch = (key: keyof typeof settings) => {
    setSettings(prev => ({ ...prev, [key]: !prev[key] }));
  };

  // 변경된 설정 서버에 저장하기
  const handleSave = async () => {
    setIsSaving(true);
    try {
      await notificationApi.updateSettings(settings);
      Alert.alert('저장 완료', '알림 설정이 성공적으로 변경되었습니다.', [
        { text: '확인', onPress: () => router.back() }
      ]);
    } catch (error) {
      Alert.alert('오류', '설정 변경에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  // 공통 세팅 로우 컴포넌트
  const SettingRow = ({ title, desc, settingKey, disabled = false }: { title: string, desc: string, settingKey: keyof typeof settings, disabled?: boolean }) => (
    <View style={[styles.settingRow, disabled && { opacity: 0.5 }]}>
      <View style={styles.textContainer}>
        <Text fontWeight="bold" style={styles.settingTitle}>{title}</Text>
        <Text style={styles.settingDesc}>{desc}</Text>
      </View>
      <Switch
        trackColor={{ false: '#E0E0E0', true: '#00A859' }}
        thumbColor="#fff"
        value={settings[settingKey] as boolean}
        onValueChange={() => toggleSwitch(settingKey)}
        disabled={disabled} 
      />
    </View>
  );

  // 변경 불가능한 필수 알림 로우 (백엔드 명세에 따라 수정 불가 처리)
  const MandatoryRow = ({ title, desc }: { title: string, desc: string }) => (
    <View style={[styles.settingRow, { opacity: 0.5 }]}>
      <View style={styles.textContainer}>
        <Text fontWeight="bold" style={styles.settingTitle}>{title}</Text>
        <Text style={styles.settingDesc}>{desc}</Text>
      </View>
      <Switch trackColor={{ true: '#00A859' }} value={true} disabled={true} />
    </View>
  );

  if (isLoading) return <View style={styles.center}><ActivityIndicator size="large" color="#00A859" /></View>;

  // 전체 알림이 꺼져있으면 하위 알림들은 시각적으로 비활성화(disabled) 처리
  const isAllDisabled = !settings.allEnabled;

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={26} color="#000" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>알림 설정</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        
        {/* 1. 마스터 설정 */}
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>전체 설정</Text>
          <SettingRow title="모든 알림 수신" desc="앱에서 발송하는 모든 알림을 켜거나 끕니다." settingKey="allEnabled" />
        </View>

        <View style={styles.divider} />

        {/* 2. 앱 푸시 알림 설정 */}
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>앱 푸시 알림</Text>
          <MandatoryRow title="주문/예약 시스템 알림" desc="결제, 조리, 예약 등 필수 정보 (해제 불가)" />
          <SettingRow title="채팅 알림" desc="이웃 또는 사장님과의 이음톡 메시지 수신" settingKey="chatEnabled" disabled={isAllDisabled} />
          <SettingRow title="커뮤니티 알림" desc="내 게시글에 반응이나 댓글이 달릴 때" settingKey="communityEnabled" disabled={isAllDisabled} />
          <SettingRow title="리뷰 알림" desc="내 가게에 새 리뷰 및 댓글이 달릴 때" settingKey="storeReviewEnabled" disabled={isAllDisabled} />
          <SettingRow title="중고거래 알림" desc="중고거래 채팅 및 상태 변경 알림" settingKey="usedProductEnabled" disabled={isAllDisabled} />
        </View>

        <View style={styles.divider} />

        {/* 3. 마케팅 알림 설정 */}
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>마케팅 알림</Text>
          <SettingRow title="이벤트 및 혜택 알림" desc="할인 쿠폰, 동네 핫딜 등 혜택 정보 (동의 기록 저장)" settingKey="marketingEnabled" disabled={isAllDisabled} />
        </View>

        <View style={styles.divider} />

        {/* 4. 이메일 수신 설정 */}
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>이메일 알림</Text>
          <SettingRow title="주문 이메일" desc="주문 내역 이메일 수신" settingKey="orderEmailEnabled" disabled={isAllDisabled} />
          <SettingRow title="예약 이메일" desc="예약 내역 이메일 수신" settingKey="reservationEmailEnabled" disabled={isAllDisabled} />
          <SettingRow title="채팅 이메일" desc="부재 중 메시지 이메일 수신" settingKey="chatEmailEnabled" disabled={isAllDisabled} />
          <SettingRow title="리뷰 이메일" desc="새 리뷰 이메일 수신" settingKey="reviewEmailEnabled" disabled={isAllDisabled} />
        </View>

        <View style={styles.divider} />

        {/* 5. 알림음 설정 */}
        {/* <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>알림음(소리) 설정</Text>
          <SettingRow title="주문 알림음" desc="주문 접수 시 소리 알림" settingKey="orderSoundEnabled" disabled={isAllDisabled} />
          <SettingRow title="예약 알림음" desc="예약 접수 시 소리 알림" settingKey="reservationSoundEnabled" disabled={isAllDisabled} />
          <SettingRow title="채팅 알림음" desc="메시지 수신 시 소리 알림" settingKey="chatSoundEnabled" disabled={isAllDisabled} />
          <SettingRow title="리뷰 알림음" desc="리뷰 작성 시 소리 알림" settingKey="reviewSoundEnabled" disabled={isAllDisabled} />
        </View> */}

      </ScrollView>

      <View style={styles.bottomBar}>
        <TouchableOpacity style={styles.saveButton} onPress={handleSave} disabled={isSaving}>
          {isSaving ? <ActivityIndicator color="#fff" /> : <Text fontWeight="bold" style={styles.saveButtonText}>설정 저장하기</Text>}
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 15, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  backButton: { marginRight: 15 },
  headerTitle: { fontSize: 18, color: '#000' },
  scrollContent: { paddingBottom: 20 },
  section: { padding: 20 },
  sectionTitle: { fontSize: 13, color: '#00A859', marginBottom: 15 },
  settingRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  textContainer: { flex: 1, paddingRight: 15 },
  settingTitle: { fontSize: 16, color: '#333', marginBottom: 4 },
  settingDesc: { fontSize: 12, color: '#888' },
  divider: { height: 8, backgroundColor: '#F8F9FA' },
  bottomBar: { padding: 20, borderTopWidth: 1, borderTopColor: '#F0F0F0' },
  saveButton: { backgroundColor: '#1B854A', paddingVertical: 16, alignItems: 'center', borderRadius: 6 },
  saveButtonText: { color: '#fff', fontSize: 16 }
});