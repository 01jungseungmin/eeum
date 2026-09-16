import React, { useState, useEffect } from 'react';
import { 
  View, StyleSheet, TextInput, TouchableOpacity, 
  Alert, ActivityIndicator, KeyboardAvoidingView, 
  Platform, ScrollView, Keyboard, TouchableWithoutFeedback
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Picker } from '@react-native-picker/picker'; 
import { Text } from '../../components/CustomText';
import { inquiryApi } from '../../api/inquiry'; 

export default function InquiryWriteScreen() {
  const router = useRouter();
  const { storeId, type } = useLocalSearchParams(); 

  const safeType = typeof type === 'string' && type !== 'undefined' ? type : 'STORE';
  const isAdminInquiry = safeType === 'ADMIN';

  const adminOptions = [
    { label: '계정/로그인', value: 'ACCOUNT' },
    { label: '상점 관련', value: 'STORE' },
    { label: '결제/환불', value: 'PAYMENT' },
    { label: '신고', value: 'REPORT' },
    { label: '기타', value: 'ETC' },
  ];

  const storeOptions = [
    { label: '주문 문의', value: 'ORDER' },
    { label: '예약 문의', value: 'RESERVATION' },
    { label: '결제/환불', value: 'PAYMENT' },
    { label: '상점 이용 문의', value: 'STORE' },
    { label: '기타', value: 'ETC' },
  ];

  // 현재 타입에 맞는 옵션 배열 선택
  const currentOptions = isAdminInquiry ? adminOptions : storeOptions;

  // 상태 관리
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isSecret, setIsSecret] = useState(false);
  
  const [category, setCategory] = useState(currentOptions[0].value);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // isAdminInquiry 값이 혹시라도 늦게 변할 때를 대비한 동기화
  useEffect(() => {
    setCategory(isAdminInquiry ? 'ACCOUNT' : 'ORDER');
  }, [isAdminInquiry]);

  const handleSubmit = async () => {
    if (!category) {
      Alert.alert('알림', '문의 유형을 선택해주세요.');
      return;
    }
    if (!title.trim()) {
      Alert.alert('알림', '문의 제목을 입력해주세요.');
      return;
    }
    if (!content.trim()) {
      Alert.alert('알림', '문의 내용을 입력해주세요.');
      return;
    }

    try {
      setIsSubmitting(true);

      await inquiryApi.createInquiry({
        targetType: isAdminInquiry ? "ADMIN" : "STORE",
        category: category, 
        storeId: isAdminInquiry ? undefined : Number(storeId),
        title: title,
        content: content,
        secret: isSecret, 
      });

      Alert.alert('성공', isAdminInquiry ? '관리자에게 문의가 접수되었습니다.' : '사장님께 문의가 접수되었습니다.', [
        { text: '확인', onPress: () => router.back() }
      ]);

    } catch (error) {
      console.error(error);
      Alert.alert('오류', '문의 등록에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>
          {isAdminInquiry ? '관리자 문의하기' : '상점 문의하기'}
        </Text>
        <View style={{ width: 26 }} />
      </View>

      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <TouchableWithoutFeedback onPress={Keyboard.dismiss}>
          <ScrollView contentContainerStyle={styles.contentContainer} keyboardShouldPersistTaps="handled">
            
            <View style={styles.inputSection}>
              <Text fontWeight="bold" style={styles.label}>문의 유형 <Text style={{color: 'red'}}>*</Text></Text>
              <View style={styles.pickerContainer}>
                
                <Picker
                  selectedValue={category}
                  onValueChange={(itemValue) => setCategory(itemValue)}
                  style={styles.picker}
                  dropdownIconColor="#333"
                >
                  {/* 안내 문구는 가장 위에 고정 */}
                  <Picker.Item label="문의 유형을 선택해주세요" value="" color="#999" />
                  
                  {/* 동적으로 옵션 렌더링 */}
                  {currentOptions.map((option) => (
                    <Picker.Item 
                      key={option.value} 
                      label={option.label} 
                      value={option.value} 
                      color="#333" 
                    />
                  ))}
                </Picker>

              </View>
            </View>

            <View style={styles.inputSection}>
              <Text fontWeight="bold" style={styles.label}>제목 <Text style={{color: 'red'}}>*</Text></Text>
              <TextInput
                style={styles.titleInput}
                placeholder="제목을 입력하세요"
                placeholderTextColor="#999"
                value={title}
                onChangeText={setTitle}
                maxLength={50}
              />
            </View>

            <View style={styles.inputSection}>
              <Text fontWeight="bold" style={styles.label}>문의 내용 <Text style={{color: 'red'}}>*</Text></Text>
              <TextInput
                style={styles.contentInput}
                placeholder="문의 내용을 자세히 입력해주세요"
                placeholderTextColor="#999"
                value={content}
                onChangeText={setContent}
                multiline
                textAlignVertical="top"
              />
            </View>

            {!isAdminInquiry && (
              <TouchableOpacity 
                style={styles.secretCheckbox} 
                activeOpacity={0.7}
                onPress={() => setIsSecret(!isSecret)}
              >
                <Ionicons name={isSecret ? "checkbox" : "square-outline"} size={22} color={isSecret ? "#00A859" : "#CCC"} />
                <Text style={styles.secretText}>비밀글로 문의하기 (사장님만 볼 수 있어요)</Text>
              </TouchableOpacity>
            )}

          </ScrollView>
        </TouchableWithoutFeedback>

        <View style={styles.bottomBar}>
          <TouchableOpacity 
            style={[
              styles.submitBtn, 
              (!title.trim() || !content.trim() || !category) && styles.submitBtnDisabled
            ]} 
            onPress={handleSubmit} 
            disabled={isSubmitting || !title.trim() || !content.trim() || !category}
          >
            {isSubmitting ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text fontWeight="bold" style={styles.submitBtnText}>문의 등록하기</Text>
            )}
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 15, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#EEE' },
  backBtn: { padding: 4 },
  headerTitle: { fontSize: 18, color: '#333' },
  contentContainer: { padding: 20 },
  
  inputSection: { marginBottom: 25 },
  label: { fontSize: 15, color: '#333', marginBottom: 10 },
  
  pickerContainer: { borderWidth: 1, borderColor: '#00A859', borderRadius: 4, overflow: 'hidden', backgroundColor: '#fff' },
  picker: { height: 50, width: '100%', color: '#333' },

  titleInput: { backgroundColor: '#fff', borderRadius: 4, paddingHorizontal: 15, paddingVertical: 12, fontSize: 15, color: '#333', borderWidth: 1, borderColor: '#DDD' },
  contentInput: { backgroundColor: '#fff', borderRadius: 4, padding: 15, fontSize: 15, color: '#333', minHeight: 180, borderWidth: 1, borderColor: '#DDD' },
  
  secretCheckbox: { flexDirection: 'row', alignItems: 'center', marginTop: -5, paddingVertical: 5 },
  secretText: { fontSize: 14, color: '#666', marginLeft: 8 },

  bottomBar: { padding: 20, backgroundColor: '#fff' },
  submitBtn: { backgroundColor: '#1B854A', paddingVertical: 16, borderRadius: 4, alignItems: 'center' },
  submitBtnDisabled: { backgroundColor: '#A5D6B7' },
  submitBtnText: { color: '#fff', fontSize: 16 },
});