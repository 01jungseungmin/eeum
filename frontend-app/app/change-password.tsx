import React, { useState } from 'react';
import { 
  View, StyleSheet, TextInput, TouchableOpacity, 
  ActivityIndicator, Alert, ScrollView 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';

import { Text } from '../components/CustomText'; 
import { userApi } from '../api/user';

export default function ChangePasswordScreen() {
  const router = useRouter();
  
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  const handlePasswordChange = async () => {
    // 1. 유효성 검사
    if (!currentPassword || !newPassword || !confirmPassword) {
      Alert.alert('알림', '모든 항목을 입력해주세요.');
      return;
    }

    if (newPassword !== confirmPassword) {
      Alert.alert('알림', '새 비밀번호가 일치하지 않습니다.');
      return;
    }

    setIsSaving(true);
    try {
      // 🔓 [1단계] 현재 비밀번호를 보내서 진짜 재인증 토큰 발급받기
      console.log("🔑 1단계: 재인증 토큰 요청 중...");
      const reauthData = await userApi.reauth({ password: currentPassword });
      
      // 백엔드가 돌려준 실제 토큰 꺼내기
      const realReAuthToken = reauthData?.reAuthToken; 

      if (!realReAuthToken) {
        Alert.alert('오류', '인증 토큰을 가져오지 못했습니다.');
        return;
      }

      // 🔒 [2단계] 발급받은 진짜 토큰을 주입해서 비밀번호 최종 변경하기
      console.log("🚀 2단계: 진짜 토큰으로 비밀번호 변경 요청 중...");
      await userApi.updatePassword({
        reAuthToken: realReAuthToken, // ✨ 따끈따끈한 진짜 토큰 주입!
        currentPassword: currentPassword,
        newPassword: newPassword
      });

      Alert.alert('성공', '비밀번호가 성공적으로 변경되었습니다.', [
        { text: '확인', onPress: () => router.back() }
      ]);
      
    } catch (error: any) {
      console.error('비밀번호 변경 실패:', error);
      
      // 백엔드 에러 메시지가 있다면 보여주고, 없으면 기본 메시지 출력
      const serverMessage = error.response?.data?.message;
      Alert.alert(
        '오류', 
        serverMessage || '비밀번호 변경에 실패했습니다. 현재 비밀번호를 다시 확인해주세요.'
      );
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={26} color="#000" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>비밀번호 변경</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
        
        <View style={styles.formSection}>
          <Text fontWeight="bold" style={styles.label}>현재 비밀번호</Text>
          <TextInput 
            style={styles.input}
            value={currentPassword}
            onChangeText={setCurrentPassword}
            placeholder="현재 비밀번호를 입력하세요"
            secureTextEntry // ✨ 비밀번호 가림 처리
          />

          <Text fontWeight="bold" style={styles.label}>새 비밀번호</Text>
          <TextInput 
            style={styles.input}
            value={newPassword}
            onChangeText={setNewPassword}
            placeholder="영문, 숫자, 특수문자 포함 8자 이상"
            secureTextEntry
          />

          <Text fontWeight="bold" style={styles.label}>새 비밀번호 확인</Text>
          <TextInput 
            style={styles.input}
            value={confirmPassword}
            onChangeText={setConfirmPassword}
            placeholder="새 비밀번호를 다시 입력하세요"
            secureTextEntry
          />
        </View>

        {/* 안내 문구 */}
        <View style={styles.infoBox}>
          <Ionicons name="information-circle-outline" size={20} color="#888" />
          <Text style={styles.infoText}>
            안전한 계정 사용을 위해 비밀번호는 주기적으로 변경해 주시는 것이 좋습니다.
          </Text>
        </View>

      </ScrollView>

      {/* 하단 고정 버튼 */}
      <View style={styles.bottomBar}>
        <TouchableOpacity style={styles.saveButton} onPress={handlePasswordChange} disabled={isSaving}>
          {isSaving ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text fontWeight="bold" style={styles.saveButtonText}>비밀번호 변경하기</Text>
          )}
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 15, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  backButton: { marginRight: 15 },
  headerTitle: { fontSize: 18, color: '#000' },
  
  scrollContent: { padding: 20 },
  
  formSection: { marginBottom: 20 },
  label: { fontSize: 14, color: '#333', marginBottom: 8, marginTop: 15 },
  input: { borderWidth: 1, borderColor: '#E8E8E8', paddingHorizontal: 15, paddingVertical: 14, fontSize: 15, color: '#333', borderRadius: 6, backgroundColor: '#FAFAFA' },
  
  infoBox: { flexDirection: 'row', backgroundColor: '#F5F5F5', padding: 15, borderRadius: 6, marginTop: 10 },
  infoText: { flex: 1, fontSize: 13, color: '#666', marginLeft: 8, lineHeight: 18 },

  bottomBar: { padding: 20, borderTopWidth: 1, borderTopColor: '#F0F0F0' },
  saveButton: { backgroundColor: '#1B854A', paddingVertical: 16, alignItems: 'center', borderRadius: 6 },
  saveButtonText: { color: '#fff', fontSize: 16 }
});