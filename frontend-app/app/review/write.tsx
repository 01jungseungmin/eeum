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
  
  // 파라미터 수신 (새로 추가된 initialImageId, initialImageUrl 포함)
  const { storeId, orderId, reservationId, reviewId, initialRating, initialContent, initialImageId, initialImageUrl } = useLocalSearchParams();
  
  const storeIdNum = typeof storeId === 'string' ? Number(storeId) : 0;
  const orderIdNum = typeof orderId === 'string' ? Number(orderId) : undefined;
  const reservationIdNum = typeof reservationId === 'string' ? Number(reservationId) : undefined;
  const reviewIdNum = typeof reviewId === 'string' ? Number(reviewId) : undefined;

  const isEditMode = !!reviewIdNum;

  // 상태값 세팅
  const [rating, setRating] = useState<number>(initialRating ? Number(initialRating) : 5);
  const [content, setContent] = useState<string>(typeof initialContent === 'string' ? initialContent : '');
  
  // 기존 서버 이미지 상태와 새로 고른 로컬 이미지 상태 분리
  const [existingImage, setExistingImage] = useState<{ id: number, url: string } | null>(
    initialImageId && initialImageUrl ? { id: Number(initialImageId), url: String(initialImageUrl) } : null
  );
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
      setImage(result.assets[0]); // 새 이미지 세팅
      setExistingImage(null);     // 새 이미지를 고르면 기존 이미지는 지워진 것으로 간주
    }
  };

  const handleSubmit = async () => {
    if (!content.trim()) {
      Alert.alert('알림', '리뷰 내용을 입력해주세요.');
      return;
    }

    try {
      setIsSubmitting(true);

      if (isEditMode && reviewIdNum) {
        await reviewApi.updateReview(storeIdNum, reviewIdNum, {
          rating: rating,
          content: content,
        });
        if (initialImageId && !existingImage) {
          await reviewApi.deleteReviewImage(storeIdNum, reviewIdNum, Number(initialImageId));
        }
        if (image) {
          await reviewApi.addReviewImages(storeIdNum, reviewIdNum, [image.uri]);
        }
        Alert.alert('성공', '리뷰가 성공적으로 수정되었습니다.');
      } 
      else {
        
        if (reservationIdNum) {
          // 1. 방문 예약 리뷰일 때: 새 전용 API 호출
          const payload = {
            rating: rating,
            content: content,
            imageUrls: image ? [image.uri] : [],
          };
          
          await reviewApi.createReservationReview(reservationIdNum, payload);
        } 
        else {
          // 2. 일반 주문 리뷰일 때: 기존 API 호출
          const payload = {
            orderId: orderIdNum,
            rating: rating,
            content: content,
            imageUrls: image ? [image.uri] : [],
          };
          
          await reviewApi.createReview(storeIdNum, payload);
        }

        Alert.alert('성공', '리뷰가 소중하게 등록되었습니다!');
      }

      router.back(); 

    } catch (error) {
      console.error(error);
      Alert.alert('오류', isEditMode ? '리뷰 수정에 실패했습니다.' : '리뷰 등록에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  // 노출할 사진 결정 (새로 고른 사진이 1순위, 기존 서버 사진이 2순위)
  const displayImageUrl = image ? image.uri : existingImage ? existingImage.url : null;

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Ionicons name="close" size={28} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>{isEditMode ? '리뷰 수정' : '리뷰 쓰기'}</Text>
        <View style={{ width: 28 }} />
      </View>

      <ScrollView contentContainerStyle={styles.contentContainer} keyboardShouldPersistTaps="handled">
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

        {/* 수정/작성 모드 상관없이 사진 UI를 보여줍니다! */}
        <Text fontWeight="bold" style={styles.sectionTitle}>사진 첨부 (선택)</Text>
        {displayImageUrl ? (
          <View style={styles.imagePreviewContainer}>
            <Image source={{ uri: displayImageUrl }} style={styles.imagePreview} />
            <TouchableOpacity 
              style={styles.removeImageBtn} 
              onPress={() => {
                setImage(null);
                setExistingImage(null); // x버튼 누르면 사진 완전 날림
              }}
            >
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
            <Text fontWeight="bold" style={styles.submitBtnText}>{isEditMode ? '수정 완료' : '등록하기'}</Text>
          )}
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

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