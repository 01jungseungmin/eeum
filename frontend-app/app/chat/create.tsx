import React, { useState } from 'react';
import { 
  View, StyleSheet, TextInput, TouchableOpacity, 
  KeyboardAvoidingView, Platform, ScrollView, Alert 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text } from '../../components/CustomText';
import { chatApi } from '../../api/chat';

export default function CreateChatRoomScreen() {
  const router = useRouter();
  
  const [roomName, setRoomName] = useState('');
  const [description, setDescription] = useState('');
  const [maxCapacity, setMaxCapacity] = useState('50');

  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleCreateRoom = async () => {
    if (!roomName.trim()) {
      Alert.alert('알림', '채팅방 이름을 입력해주세요.');
      return;
    }

    try {
      setIsSubmitting(true);

      const payload = {
        name: roomName,
        description: description,
        maxParticipants: Number(maxCapacity) || 50
      };

      await chatApi.createGroupRoom(payload);

      Alert.alert('성공', '새로운 채팅방이 개설되었습니다!', [
        { text: '확인', onPress: () => router.back() }
      ]);
      
    } catch (error) {
      console.error('채팅방 개설 에러:', error);
      Alert.alert('오류', '채팅방 개설에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.headerIcon}>
          <Ionicons name="close" size={28} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>오픈채팅방 만들기</Text>
        <TouchableOpacity onPress={handleCreateRoom} disabled={isSubmitting} style={styles.headerIcon}>
          <Text fontWeight="bold" style={[styles.submitText, roomName.trim() && !isSubmitting ? { color: '#1B854A' } : { color: '#CCC' }]}>
            완료
          </Text>
        </TouchableOpacity>
      </View>

      <KeyboardAvoidingView 
        style={{ flex: 1 }} 
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
          
          <View style={styles.imageSection}>
            <TouchableOpacity style={styles.imagePickerBtn}>
              <Ionicons name="camera" size={32} color="#999" />
              <View style={styles.badgePlus}>
                <Ionicons name="add" size={14} color="#FFF" />
              </View>
            </TouchableOpacity>
            <Text style={styles.imageSectionText}>프로필 사진 등록</Text>
          </View>

          <View style={styles.formSection}>
            <View style={styles.inputGroup}>
              <Text fontWeight="bold" style={styles.label}>채팅방 이름</Text>
              <TextInput
                style={styles.input}
                placeholder="어떤 대화를 나누고 싶나요?"
                placeholderTextColor="#999"
                maxLength={30}
                value={roomName}
                onChangeText={setRoomName}
              />
              <Text style={styles.charCount}>{roomName.length}/30</Text>
            </View>

            <View style={styles.inputGroup}>
              <Text fontWeight="bold" style={styles.label}>소개 태그 (선택)</Text>
              <TextInput
                style={styles.input}
                placeholder="#맛집탐방 #동네친구 처럼 적어보세요"
                placeholderTextColor="#999"
                maxLength={50}
                value={description}
                onChangeText={setDescription}
              />
            </View>

            <View style={styles.inputGroup}>
              <Text fontWeight="bold" style={styles.label}>최대 인원 수</Text>
              <View style={styles.capacityRow}>
                <TextInput
                  style={[styles.input, { flex: 1, textAlign: 'right', paddingRight: 40 }]}
                  keyboardType="numeric"
                  value={maxCapacity}
                  onChangeText={setMaxCapacity}
                />
                <Text style={styles.capacityUnit}>명</Text>
              </View>
            </View>
          </View>

          <View style={styles.guideBox}>
            <Ionicons name="information-circle" size={18} color="#888" style={{ marginRight: 6 }} />
            <Text style={styles.guideText}>
              불건전한 만남 목적이나 욕설이 포함된 방 이름은 경고 없이 삭제될 수 있습니다.
            </Text>
          </View>

        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFF' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 15, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  headerIcon: { padding: 4, minWidth: 44, alignItems: 'center', justifyContent: 'center' },
  headerTitle: { fontSize: 17, color: '#333' },
  submitText: { fontSize: 16 },
  scrollContent: { padding: 20 },
  imageSection: { alignItems: 'center', marginVertical: 30 },
  imagePickerBtn: { width: 90, height: 90, borderRadius: 30, backgroundColor: '#F5F6F8', justifyContent: 'center', alignItems: 'center', borderWidth: 1, borderColor: '#EAEAEA' },
  badgePlus: { position: 'absolute', bottom: -5, right: -5, backgroundColor: '#1B854A', width: 26, height: 26, borderRadius: 13, justifyContent: 'center', alignItems: 'center', borderWidth: 2, borderColor: '#FFF' },
  imageSectionText: { marginTop: 12, fontSize: 14, color: '#666' },
  formSection: { marginBottom: 30 },
  inputGroup: { marginBottom: 24, position: 'relative' },
  label: { fontSize: 14, color: '#333', marginBottom: 10 },
  input: { borderBottomWidth: 1, borderBottomColor: '#DDD', paddingVertical: 10, fontSize: 16, color: '#333' },
  charCount: { position: 'absolute', right: 0, bottom: 12, fontSize: 12, color: '#999' },
  capacityRow: { flexDirection: 'row', alignItems: 'center', position: 'relative' },
  capacityUnit: { position: 'absolute', right: 0, bottom: 12, fontSize: 16, color: '#333' },
  guideBox: { flexDirection: 'row', backgroundColor: '#F8F9FA', padding: 16, borderRadius: 8, alignItems: 'flex-start' },
  guideText: { flex: 1, fontSize: 13, color: '#777', lineHeight: 18 },
});