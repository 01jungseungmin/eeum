import React, { useState } from 'react';
import { 
  View, StyleSheet, TextInput, TouchableOpacity, 
  ActivityIndicator, Alert, ScrollView 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';

import { Text } from '../../components/CustomText'; 
import { userApi } from '../../api/user';
import { clearTokens } from '../../utils/secureStore'; // 로그아웃 처리를 위한 토큰 삭제 함수

export default function WithdrawScreen() {
  const router = useRouter();
  
  const [password, setPassword] = useState('');
  const [isChecked, setIsChecked] = useState(false); // 유의사항 동의 여부
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleWithdraw = async () => {
    // 1. 유의사항 동의 체크 여부 확인
    if (!isChecked) {
      Alert.alert('알림', '탈퇴 유의사항을 확인하고 동의해 주세요.');
      return;
    }

    // 2. 비밀번호 입력 확인
    if (!password.trim()) {
      Alert.alert('알림', '본인 확인을 위해 비밀번호를 입력해 주세요.');
      return;
    }

    Alert.alert(
      '회원 탈퇴',
      '정말로 탈퇴하시겠습니까? 이 작업은 되돌릴 수 없습니다.',
      [
        { text: '취소', style: 'cancel' },
        { 
          text: '탈퇴하기', 
          style: 'destructive',
          onPress: async () => {
            setIsSubmitting(true);
            try {
              // 🔓 [1단계] 비밀번호를 보내서 재인증 토큰 발급받기
              console.log("🔑 탈퇴 1단계: 재인증 토큰 요청 중...");
              const reauthData = await userApi.reauth({ password: password });
              const realReAuthToken = reauthData?.reAuthToken;

              if (!realReAuthToken) {
                Alert.alert('오류', '인증 토큰을 가져오지 못했습니다.');
                return;
              }

              // ❌ [2단계] 발급받은 진짜 토큰을 들고 최종 탈퇴 API 호출하기
              console.log("🚀 탈퇴 2단계: 최종 탈퇴 처리 중...");
              await userApi.deleteAccount({ reAuthToken: realReAuthToken });

              // 🧹 [3단계] 탈퇴 성공 시 로컬 기기의 로그인 토큰 비우기
              await clearTokens();

              Alert.alert('탈퇴 완료', '회원 탈퇴가 정상적으로 처리되었습니다. 그동안 이용해 주셔서 감사합니다.', [
                { 
                  text: '확인', 
                  onPress: () => {
                    // 로그인 화면이나 메인 시작 화면으로 초기화하면서 이동
                    router.replace('/login' as any); 
                  } 
                }
              ]);

            } catch (error: any) {
              console.error('회원 탈퇴 실패:', error);
              const serverMessage = error.response?.data?.message;
              Alert.alert(
                '오류', 
                serverMessage || '비밀번호가 일치하지 않거나 탈퇴 처리에 실패했습니다.'
              );
            } finally {
              setIsSubmitting(false);
            }
          }
        }
      ]
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={26} color="#000" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>회원 탈퇴</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
        
        {/* ⚠️ 탈퇴 유의사항 안내 상자 */}
        <View style={styles.warningBox}>
          <View style={styles.warningHeader}>
            <Ionicons name="warning" size={20} color="#E74C3C" />
            <Text fontWeight="bold" style={styles.warningTitle}>회원 탈퇴 전 꼭 확인해 주세요</Text>
          </View>
          
          <Text style={styles.warningText}>• 회원 탈퇴 시 기존의 개인 정보 및 이용 내역은 모두 삭제됩니다.</Text>
          <Text style={styles.warningText}>• 데이터는 즉시 삭제되지 않으며, 30일간 유예 기간을 거친 후 완전히 물리 삭제 처리됩니다.</Text>
          <Text style={styles.warningText}>• 작성하신 게시글이나 댓글은 탈퇴 후에도 자동으로 삭제되지 않으니, 삭제를 원하시면 탈퇴 전에 직접 삭제해 주시기 바랍니다.</Text>
        </View>

        {/* 동네방네 약관 동의 체크 UI */}
        <TouchableOpacity 
          style={styles.checkboxRow} 
          onPress={() => setIsChecked(!isChecked)}
          activeOpacity={0.8}
        >
          <Ionicons 
            name={isChecked ? "checkbox" : "square-outline"} 
            size={24} 
            color={isChecked ? "#1B854A" : "#CCC"} 
          />
          <Text style={styles.checkboxLabel}>위의 회원 탈퇴 유의사항을 모두 확인했으며 동의합니다.</Text>
        </TouchableOpacity>

        {/* 비밀번호 입력 확인 폼 */}
        <View style={styles.formSection}>
          <Text fontWeight="bold" style={styles.label}>비밀번호 확인</Text>
          <TextInput 
            style={styles.input}
            value={password}
            onChangeText={setPassword}
            placeholder="본인 확인을 위해 현재 비밀번호를 입력하세요"
            secureTextEntry
          />
        </View>

      </ScrollView>

      {/* 하단 탈퇴 확정 버튼 */}
      <View style={styles.bottomBar}>
        <TouchableOpacity 
          style={[styles.withdrawButton, !isChecked && styles.disabledButton]} 
          onPress={handleWithdraw} 
          disabled={isSubmitting}
        >
          {isSubmitting ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text fontWeight="bold" style={styles.withdrawButtonText}>탈퇴하기</Text>
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
  
  warningBox: { backgroundColor: '#FFF5F5', borderWidth: 1, borderColor: '#FFD3D3', padding: 18, borderRadius: 8, marginBottom: 20 },
  warningHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
  warningTitle: { fontSize: 15, color: '#E74C3C', marginLeft: 8 },
  warningText: { fontSize: 13, color: '#555', lineHeight: 20, marginBottom: 6 },
  
  checkboxRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 10, marginBottom: 25 },
  checkboxLabel: { flex: 1, fontSize: 14, color: '#333', marginLeft: 10, lineHeight: 20 },

  formSection: { marginBottom: 20 },
  label: { fontSize: 14, color: '#333', marginBottom: 8 },
  input: { borderWidth: 1, borderColor: '#E8E8E8', paddingHorizontal: 15, paddingVertical: 14, fontSize: 15, color: '#333', borderRadius: 6, backgroundColor: '#FAFAFA' },
  
  bottomBar: { padding: 20, borderTopWidth: 1, borderTopColor: '#F0F0F0' },
  withdrawButton: { backgroundColor: '#E74C3C', paddingVertical: 16, alignItems: 'center', borderRadius: 6 },
  disabledButton: { backgroundColor: '#FABEBE' }, // 체크 해제 상태일 때 비활성화 느낌의 연한 붉은색
  withdrawButtonText: { color: '#fff', fontSize: 16 }
});