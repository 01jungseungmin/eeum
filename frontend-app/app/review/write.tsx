import React, { useState } from 'react';
import { View, StyleSheet, TextInput, TouchableOpacity, Image, Alert, ActivityIndicator, ScrollView } from 'react-native';
import { Text } from '../../components/CustomText';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';

import { reviewApi } from '../../api/review';

export default function ReviewWriteScreen() {
  const router = useRouter();
  const { storeId, orderId } = useLocalSearchParams();
  const storeIdNum = typeof storeId === 'string' ? Number(storeId) : 0;
  const orderIdNum = typeof orderId === 'string' ? Number(orderId) : 0

  const [rating, setRating] = useState<number>(5);
  const [content, setContent] = useState<string>('');
  const [image, setImage] = useState<ImagePicker.ImagePickerAsset | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const pickImage = async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('권한 필요', '사진 앨범 접근 권한이 필요합니다.');
      return;
    }
    let result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true,
      aspect: [4, 3],
      quality: 0.8,
    });
    if (!result.canceled) {
      setImage(result.assets[0]);
    }
  };

  const handleSubmit = async () => {
    if (!content.trim()) {
      Alert.alert('알림', '리뷰 내용을 입력해주세요.');
      return;
    }

    try {
      setIsSubmitting(true);

      // ✨ 중요: 스웨거 명세에 맞춰서 페이로드 구성
      // orderId는 실제 앱에서는 결제 내역에서 받아와야 하지만, 지금은 임시로 42를 넣습니다.
      const payload = {
        orderId: orderIdNum, 
        rating: rating,
        content: content,
        imageUrls: image ? [image.uri] : [] // 폼데이터 대신 로컬 URI를 배열로 전송
      };

      await reviewApi.createReview(storeIdNum, payload);

      Alert.alert('성공', '리뷰가 소중하게 등록되었습니다!');
      router.back(); 

    } catch (error) {
      console.error(error);
      Alert.alert('오류', '리뷰 등록에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  // presigned로 할 때
  // const handleSubmit = async () => {
  //   if (!content.trim()) {
  //     Alert.alert('알림', '리뷰 내용을 입력해주세요.');
  //     return;
  //   }

  //   try {
  //     setIsSubmitting(true);
      
  //     let uploadedImageUrl = ''; // 최종적으로 S3에 올라간 이미지 주소

  //     // 📸 이미지가 있을 경우에만 Presigned URL 1~2단계 실행
  //     if (image) {
  //       // ✨ [1단계] 백엔드에 Presigned URL 발급 요청 (API 추가 필요)
  //       // 확장자나 파일명을 보내야 할 수도 있습니다. 파트너와 상의가 필요해요!
  //       const ext = image.uri.split('.').pop(); 
  //       const urlRes = await reviewApi.getPresignedUrl({ extension: ext });
        
  //       // 백엔드가 내려준 S3 업로드용 임시 주소와, 나중에 쓰일 실제 이미지 주소
  //       const presignedUrl = urlRes.presignedUrl; 
  //       const finalImageUrl = urlRes.imageUrl;

  //       // ✨ [2단계] S3(또는 클라우드)에 이미지 직접 업로드
  //       // Expo에서는 로컬 uri를 Blob 형태로 변환해서 올려야 합니다.
  //       const response = await fetch(image.uri);
  //       const blob = await response.blob();

  //       await fetch(presignedUrl, {
  //         method: 'PUT',
  //         body: blob,
  //         headers: {
  //           'Content-Type': `image/${ext}`, // 예: image/jpeg
  //         },
  //       });

  //       // 업로드 성공 시 최종 이미지 주소 저장
  //       uploadedImageUrl = finalImageUrl;
  //     }

  //     // ✨ [3단계] 리뷰 내용과 최종 이미지 주소를 묶어서 백엔드에 전송 (기존 코드와 유사)
  //     const payload = {
  //       orderId: orderIdNum, 
  //       rating: rating,
  //       content: content,
  //       // 이미지가 없으면 빈 배열, 있으면 업로드된 주소를 배열에 담아 보냅니다.
  //       imageUrls: uploadedImageUrl ? [uploadedImageUrl] : [] 
  //     };

  //     await reviewApi.createReview(storeIdNum, payload);

  //     Alert.alert('성공', '리뷰가 소중하게 등록되었습니다!');
  //     router.back(); 

  //   } catch (error) {
  //     console.error('리뷰 등록 에러:', error);
  //     Alert.alert('오류', '리뷰 등록에 실패했습니다.');
  //   } finally {
  //     setIsSubmitting(false);
  //   }
  // };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Ionicons name="close" size={28} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>리뷰 쓰기</Text>
        <View style={{ width: 28 }} />
      </View>

      <ScrollView contentContainerStyle={styles.contentContainer}>
        <View style={styles.ratingSection}>
          <Text fontWeight="bold" style={styles.sectionTitle}>이 상점 어떠셨나요?</Text>
          <View style={styles.starsRow}>
            {[1, 2, 3, 4, 5].map((star) => (
              <TouchableOpacity key={star} onPress={() => setRating(star)}>
                <Ionicons name={star <= rating ? "star" : "star-outline"} size={40} color="#FFD700" style={{ marginHorizontal: 5 }} />
              </TouchableOpacity>
            ))}
          </View>
        </View>

        <TextInput
          style={styles.textInput}
          placeholder="다른 고객들에게 도움이 될 수 있도록 솔직한 리뷰를 남겨주세요."
          multiline
          textAlignVertical="top"
          value={content}
          onChangeText={setContent}
        />

        <Text fontWeight="bold" style={styles.sectionTitle}>사진 첨부 (선택)</Text>
        {image ? (
          <View style={styles.imagePreviewContainer}>
            <Image source={{ uri: image.uri }} style={styles.imagePreview} />
            <TouchableOpacity style={styles.removeImageBtn} onPress={() => setImage(null)}>
              <Ionicons name="close-circle" size={24} color="#FF5252" />
            </TouchableOpacity>
          </View>
        ) : (
          <TouchableOpacity style={styles.imagePickerBtn} onPress={pickImage}>
            <Ionicons name="camera-outline" size={32} color="#888" />
            <Text style={styles.imagePickerText}>사진 추가하기</Text>
          </TouchableOpacity>
        )}
      </ScrollView>

      <View style={styles.bottomBar}>
        <TouchableOpacity style={styles.submitBtn} onPress={handleSubmit} disabled={isSubmitting}>
          {isSubmitting ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text fontWeight="bold" style={styles.submitBtnText}>등록하기</Text>
          )}
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

// 스타일은 이전과 동일하게 유지합니다.
const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#EEE' },
  backBtn: { padding: 4 },
  headerTitle: { fontSize: 18, color: '#333' },
  contentContainer: { padding: 20 },
  ratingSection: { alignItems: 'center', marginBottom: 30 },
  sectionTitle: { fontSize: 16, color: '#333', marginBottom: 15 },
  starsRow: { flexDirection: 'row' },
  textInput: { backgroundColor: '#F8F8F8', borderRadius: 8, padding: 15, fontSize: 15, minHeight: 150, marginBottom: 30 },
  imagePickerBtn: { borderWidth: 1, borderColor: '#DDD', borderStyle: 'dashed', borderRadius: 8, height: 100, alignItems: 'center', justifyContent: 'center' },
  imagePickerText: { marginTop: 8, color: '#888', fontSize: 14 },
  imagePreviewContainer: { position: 'relative', width: 100, height: 100 },
  imagePreview: { width: 100, height: 100, borderRadius: 8 },
  removeImageBtn: { position: 'absolute', top: -10, right: -10, backgroundColor: '#fff', borderRadius: 12 },
  bottomBar: { padding: 20, borderTopWidth: 1, borderTopColor: '#EEE' },
  submitBtn: { backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  submitBtnText: { color: '#fff', fontSize: 16 },
});