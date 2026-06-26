import React, { useState, useEffect } from 'react';
import { 
  View, StyleSheet, TextInput, TouchableOpacity, Image, 
  ActivityIndicator, Alert, ScrollView 
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as ImagePicker from 'expo-image-picker';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';

import { Text } from '../../components/CustomText'; 
import { userApi, MyInfoResponse } from '../../api/user';

export default function EditProfileScreen() {
  const router = useRouter();
  
  const [userInfo, setUserInfo] = useState<MyInfoResponse | null>(null);
  const [nickname, setNickname] = useState('');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [profileImage, setProfileImage] = useState<string | null>(null);
  
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    const fetchMyInfo = async () => {
      try {
        const data = await userApi.getMyInfo();
        setUserInfo(data);
        setNickname(data.nickname || '');
        setName(data.name || '');
        setPhone(data.phone || ''); 
        setProfileImage(data.profileImageUrl || null);
        
      } catch (error) {
        console.error('내 정보 로딩 에러:', error);
        Alert.alert('오류', '내 정보를 불러오는데 실패했습니다.');
      } finally {
        setIsLoading(false);
      }
    };
    fetchMyInfo();
  }, []);

  const pickImage = async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('권한 필요', '사진 앨범 접근 권한이 필요합니다.');
      return;
    }

    let result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.8,
    });

    if (!result.canceled) {
      setProfileImage(result.assets[0].uri);
    }
  };

  const handleSave = async () => {
    if (!nickname.trim()) {
      Alert.alert('알림', '닉네임을 입력해주세요.');
      return;
    }

    setIsSaving(true);
    try {
      // 정보 수정 API 스펙에 맞춰 닉네임과 프로필 이미지만 전송합니다.
      await userApi.updateProfile({
        nickname: nickname,
        profileImageUrl: profileImage || '' 
      });

      Alert.alert('성공', '회원정보가 성공적으로 수정되었습니다.');
      router.back();
    } catch (error) {
      console.error('정보 수정 에러:', error);
      Alert.alert('오류', '정보 수정에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#1B854A" />
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={26} color="#000" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>회원정보 수정</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        
        <View style={styles.profileSection}>
          <View style={styles.imageWrapper}>
            {profileImage ? (
              <Image source={{ uri: profileImage }} style={styles.profileImage} />
            ) : (
              <View style={styles.placeholderImage}>
                <Ionicons name="image-outline" size={50} color="#CCC" />
              </View>
            )}
            <TouchableOpacity style={styles.cameraBadge} onPress={pickImage}>
              <Ionicons name="camera" size={16} color="#fff" />
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.formSection}>
          <Text fontWeight="bold" style={styles.label}>닉네임</Text>
          <TextInput 
            style={styles.input}
            value={nickname}
            onChangeText={setNickname}
            placeholder="닉네임을 입력하세요"
          />

          <Text fontWeight="bold" style={styles.label}>이름</Text>
          <TextInput 
            style={[styles.input, styles.disabledInput]}
            value={name}
            editable={false}
          />

          <Text fontWeight="bold" style={styles.label}>전화번호</Text>
          <TextInput 
            style={[styles.input, styles.disabledInput]}
            value={phone}
            editable={false}
          />

          <Text fontWeight="bold" style={styles.label}>이메일</Text>
          <TextInput 
            style={[styles.input, styles.disabledInput]}
            value={userInfo?.email}
            editable={false}
          />
          <Text style={styles.hintText}>이름, 전화번호, 이메일은 변경할 수 없습니다</Text>
        </View>

        <TouchableOpacity style={styles.saveButton} onPress={handleSave} disabled={isSaving}>
          {isSaving ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text fontWeight="bold" style={styles.saveButtonText}>저장</Text>
          )}
        </TouchableOpacity>

      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 15, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  backButton: { marginRight: 15 },
  headerTitle: { fontSize: 18, color: '#000' },

  scrollContent: { padding: 20, paddingBottom: 40 },

  profileSection: { alignItems: 'center', marginBottom: 30, marginTop: 10 },
  imageWrapper: { position: 'relative' },
  profileImage: { width: 100, height: 100, borderRadius: 50 },
  placeholderImage: { width: 100, height: 100, borderRadius: 50, backgroundColor: '#F5F5F5', justifyContent: 'center', alignItems: 'center', borderWidth: 1, borderColor: '#E0E0E0' },
  cameraBadge: { position: 'absolute', bottom: 0, right: 0, backgroundColor: '#1B854A', width: 30, height: 30, borderRadius: 15, justifyContent: 'center', alignItems: 'center', borderWidth: 2, borderColor: '#fff' },

  formSection: { marginBottom: 30 },
  label: { fontSize: 14, color: '#333', marginBottom: 8, marginTop: 15 },
  input: { borderWidth: 1, borderColor: '#E8E8E8', paddingHorizontal: 15, paddingVertical: 12, fontSize: 15, color: '#333', borderRadius: 4 },
  disabledInput: { backgroundColor: '#F9F9F9', color: '#888' },
  hintText: { fontSize: 12, color: '#888', marginTop: 6 },

  saveButton: { backgroundColor: '#1B854A', paddingVertical: 15, alignItems: 'center', borderRadius: 4 },
  saveButtonText: { color: '#fff', fontSize: 16 }
});