import React, { useState, useEffect } from 'react';
import { View, StyleSheet, TouchableOpacity, Switch, ActivityIndicator, Alert, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../components/CustomText';
import { notificationApi } from '../api/notification';

export default function NotificationSettingsScreen() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  // 백엔드 명세에 맞춘 설정 상태들
  const [settings, setSettings] = useState({
    orderEnabled: true,
    reservationEnabled: true,
    chatEnabled: true,
    communityEnabled: true,
    storeReviewEnabled: true,
    marketingEnabled: false,
  });

  // 초기 설정값 불러오기
  useEffect(() => {
    const fetchSettings = async () => {
      try {
        const res = await notificationApi.getSettings();
        if (res.data) setSettings(res.data);
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

  const SettingRow = ({ title, desc, settingKey, disabled = false }: any) => (
    <View style={styles.settingRow}>
      <View style={styles.textContainer}>
        <Text fontWeight="bold" style={[styles.settingTitle, disabled && { color: '#999' }]}>{title}</Text>
        <Text style={styles.settingDesc}>{desc}</Text>
      </View>
      <Switch
        trackColor={{ false: '#E0E0E0', true: '#00A859' }}
        thumbColor="#fff"
        value={settings[settingKey as keyof typeof settings]}
        onValueChange={() => toggleSwitch(settingKey)}
        disabled={disabled} // 주문, 예약 등 필수 알림은 끄지 못하게 막음
      />
    </View>
  );

  if (isLoading) return <View style={styles.center}><ActivityIndicator size="large" color="#00A859" /></View>;

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={26} color="#000" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>알림 설정</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>필수 알림 (해제 불가)</Text>
          <SettingRow title="주문 진행 상황 알림" desc="결제 완료, 조리 시작 등 필수 주문 정보" settingKey="orderEnabled" disabled={true} />
          <SettingRow title="예약 관련 알림" desc="예약 확정, 취소, 거절 알림" settingKey="reservationEnabled" disabled={true} />
        </View>

        <View style={styles.divider} />

        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>일반 알림 설정</Text>
          <SettingRow title="채팅 알림" desc="이웃 또는 사장님과의 이음톡 메시지 수신" settingKey="chatEnabled" />
          <SettingRow title="리뷰 알림" desc="내 가게에 새 리뷰가 달리거나 댓글이 달릴 때" settingKey="storeReviewEnabled" />
          <SettingRow title="커뮤니티 알림" desc="내 게시글에 반응이 있을 때" settingKey="communityEnabled" />
        </View>

        <View style={styles.divider} />

        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>마케팅 알림</Text>
          <SettingRow title="이벤트 및 혜택 알림" desc="할인 쿠폰, 동네 핫딜 등 혜택 정보 (동의 기록 저장)" settingKey="marketingEnabled" />
        </View>
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