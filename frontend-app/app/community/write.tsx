import React, { useState, useEffect } from 'react';
import { View, StyleSheet, TouchableOpacity, TextInput, Alert, ScrollView, ActivityIndicator, Image, KeyboardAvoidingView, Platform } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import * as ImagePicker from 'expo-image-picker'; 
import { Text } from '../../components/CustomText';
import { communityApi } from '../../api/community';

const CATEGORY_MAP = [
  { id: 8, name: '자유게시판' },
  { id: 9, name: '동네소식' },
  { id: 10, name: '분실물' },
  { id: 11, name: '도움요청' },
  { id: 12, name: '공동배달' },
];

// 이미지 관리를 위한 타입 정의 (기존 이미지인지 새 이미지인지 구분)
interface ImageItem {
  id?: number; // 백엔드에 이미 저장된 이미지면 id가 있음
  uri: string;
}

export default function CommunityWriteScreen() {
  const router = useRouter();
  const { editId } = useLocalSearchParams(); 
  
  const [selectedCategory, setSelectedCategory] = useState<number | null>(null);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  // 선택된 이미지 배열 구조 변경
  const [selectedImages, setSelectedImages] = useState<ImageItem[]>([]);
  // 삭제할 기존 이미지의 ID들을 모아두는 배열
  const [deletedImageIds, setDeletedImageIds] = useState<number[]>([]);

  useEffect(() => {
    if (editId) {
      const fetchExistingPost = async () => {
        try {
          const data = await communityApi.getPostDetail(editId as string);
          setTitle(data.title);     
          setContent(data.content); 
          if (data.categoryId) setSelectedCategory(data.categoryId);
          
          // 백엔드에서 받아온 기존 이미지들을 id와 uri로 분리해서 저장
          if (data.images && data.images.length > 0) {
            const existingImages = data.images.map((img: any) => ({
              id: img.imageId,
              uri: img.imageUrl
            }));
            setSelectedImages(existingImages);
          }
        } catch (error) {
          console.error('기존 글 불러오기 실패:', error);
        }
      };
      fetchExistingPost();
    }
  }, [editId]);

  const pickImage = async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('권한 필요', '사진첩 접근 권한이 필요합니다.');
      return;
    }

    let result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsMultipleSelection: true,
      selectionLimit: 5 - selectedImages.length, 
      quality: 0.8,
    });

    if (!result.canceled) {
      const newImages = result.assets.map(asset => ({ uri: asset.uri }));
      setSelectedImages((prev) => [...prev, ...newImages].slice(0, 5));
    }
  };

  const removeImage = (indexToRemove: number) => {
    const targetImage = selectedImages[indexToRemove];
    
    // 기존에 서버에 있던 이미지를 삭제한 거라면 삭제 명단에 기록해둡니다.
    if (targetImage.id) {
      setDeletedImageIds(prev => [...prev, targetImage.id!]);
    }
    
    setSelectedImages((prev) => prev.filter((_, index) => index !== indexToRemove));
  };

  const handleSubmit = async () => {
    if (!selectedCategory) return Alert.alert('알림', '카테고리를 선택해주세요.');
    if (!title.trim()) return Alert.alert('알림', '제목을 입력해주세요.');
    if (!content.trim()) return Alert.alert('알림', '내용을 입력해주세요.');

    try {
      setIsSubmitting(true);
      
      const postData = {
        categoryId: selectedCategory,
        title: title,
        content: content,
      };

      let currentPostId = editId as string;

      if (editId) {
        // 1. 게시글 텍스트 업데이트
        await communityApi.updatePost(currentPostId, postData);
        
        // 2. 삭제 처리된 기존 이미지가 있다면 백엔드에 삭제 요청!
        if (deletedImageIds.length > 0) {
          await Promise.all(
            deletedImageIds.map(imgId => communityApi.deletePostImage(currentPostId, imgId))
          );
        }
      } else {
        // [작성 모드] 1. 새 게시글 생성
        const res = await communityApi.createPost(postData);
        // 백엔드 응답에서 생성된 postId를 추출합니다. (응답 명세에 따라 postId, communityPostId, id 등으로 변경 필요할 수 있음)
        currentPostId = res.data?.communityPostId || res.data?.postId || res.data?.id || res.id;
      }

      // 3. 새롭게 추가된 이미지가 있다면 백엔드에 등록 요청!
      const newImages = selectedImages.filter(img => !img.id);
      if (newImages.length > 0 && currentPostId) {
        const imagePayload = newImages.map((img, index) => ({
          imageUrl: img.uri, 
          thumbnail: index === 0 && selectedImages[0].uri === img.uri
        }));
        
        await communityApi.uploadPostImages(currentPostId, imagePayload);
      }

      Alert.alert('성공', editId ? '게시글이 수정되었습니다.' : '게시글이 등록되었습니다.', [
        { text: '확인', onPress: () => router.back() }
      ]);
      
    } catch (error: any) {
      console.error('작성/수정 에러:', error);
      Alert.alert('오류', '작성에 실패했습니다. 서버 로그를 확인해주세요.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const isFormValid = selectedCategory !== null && title.trim().length > 0 && content.trim().length > 0;

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.headerIconBtn}>
          <Ionicons name="chevron-back" size={26} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>
          {editId ? '글 수정' : '글쓰기'}
        </Text>
        <TouchableOpacity onPress={handleSubmit} disabled={isSubmitting || !isFormValid} style={styles.headerIconBtn}>
          {isSubmitting ? (
            <ActivityIndicator size="small" color="#1B854A" />
          ) : (
            <Text fontWeight="bold" style={[styles.submitTextTop, isFormValid ? { color: '#1B854A' } : { color: '#999' }]}>
              완료
            </Text>
          )}
        </TouchableOpacity>
      </View>

      <KeyboardAvoidingView 
        style={{ flex: 1 }} 
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">
          
          <View style={styles.section}>
            <Text fontWeight="bold" style={styles.sectionLabel}>카테고리</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.categoryScroll}>
              {CATEGORY_MAP.map((cat) => (
                <TouchableOpacity
                  key={cat.id}
                  style={[styles.categoryChip, selectedCategory === cat.id && styles.activeCategoryChip]}
                  onPress={() => setSelectedCategory(cat.id)}
                >
                  <Text style={[styles.categoryText, selectedCategory === cat.id && styles.activeCategoryText]}>
                    {cat.name}
                  </Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
          </View>

          <View style={styles.section}>
            <Text fontWeight="bold" style={styles.sectionLabel}>제목</Text>
            <TextInput
              style={styles.inputBox}
              placeholder="제목을 입력하세요"
              placeholderTextColor="#999"
              maxLength={50}
              value={title}
              onChangeText={setTitle}
            />
          </View>

          <View style={styles.section}>
            <Text fontWeight="bold" style={styles.sectionLabel}>내용</Text>
            <TextInput
              style={[styles.inputBox, styles.contentInputBox]}
              placeholder="내용을 입력하세요"
              placeholderTextColor="#999"
              multiline
              textAlignVertical="top"
              value={content}
              onChangeText={setContent}
            />
          </View>

          <View style={styles.section}>
            <Text fontWeight="bold" style={styles.sectionLabel}>사진 ({selectedImages.length}/5)</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.photoScroll}>
              <TouchableOpacity style={styles.photoAddBtn} onPress={pickImage} disabled={selectedImages.length >= 5}>
                <Ionicons name="add" size={32} color="#999" />
              </TouchableOpacity>
              {selectedImages.map((img, index) => (
                <View key={index} style={styles.imagePreviewWrapper}>
                  <Image source={{ uri: img.uri }} style={styles.imagePreview} />
                  <TouchableOpacity style={styles.removeImageBtn} onPress={() => removeImage(index)}>
                    <Ionicons name="close" size={16} color="#FFF" />
                  </TouchableOpacity>
                </View>
              ))}
            </ScrollView>
          </View>

          <View style={styles.guideBox}>
            <Text fontWeight="bold" style={styles.guideTitle}>게시글 작성 안내</Text>
            <Text style={styles.guideText}>• 욕설, 비방, 허위 사실은 삭제될 수 있습니다.</Text>
            <Text style={styles.guideText}>• 상업적 광고는 금지됩니다.</Text>
            <Text style={styles.guideText}>• 개인정보를 공개하지 마세요.</Text>
          </View>

        </ScrollView>
      </KeyboardAvoidingView>

      <View style={styles.bottomContainer}>
        <TouchableOpacity 
          style={[styles.bottomSubmitBtn, isFormValid ? styles.bottomSubmitBtnActive : null]}
          onPress={handleSubmit}
          disabled={isSubmitting || !isFormValid}
        >
          <Text fontWeight="bold" style={[styles.bottomSubmitBtnText, isFormValid ? { color: '#FFF' } : null]}>
            작성 완료
          </Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 10, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  headerIconBtn: { padding: 10, minWidth: 50, alignItems: 'center' },
  headerTitle: { fontSize: 16, color: '#333' },
  submitTextTop: { fontSize: 15 },
  scrollContent: { padding: 20, paddingBottom: 40 },
  section: { marginBottom: 24 },
  sectionLabel: { fontSize: 14, color: '#222', marginBottom: 12 },
  categoryScroll: { paddingRight: 20 },
  categoryChip: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, backgroundColor: '#F5F6F8', marginRight: 10 },
  activeCategoryChip: { backgroundColor: '#1B854A' },
  categoryText: { fontSize: 14, color: '#555' },
  activeCategoryText: { color: '#fff', fontWeight: 'bold' },
  inputBox: { borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 8, paddingHorizontal: 15, paddingVertical: 12, fontSize: 15, color: '#333', backgroundColor: '#FFF' },
  contentInputBox: { minHeight: 180, paddingTop: 15 },
  photoScroll: { flexDirection: 'row' },
  photoAddBtn: { width: 70, height: 70, borderWidth: 1, borderColor: '#CCC', borderStyle: 'dashed', borderRadius: 4, justifyContent: 'center', alignItems: 'center', marginRight: 12, backgroundColor: '#FAFAFA' },
  imagePreviewWrapper: { marginRight: 12, position: 'relative' },
  imagePreview: { width: 70, height: 70, borderRadius: 4, backgroundColor: '#F0F0F0' },
  removeImageBtn: { position: 'absolute', top: -6, right: -6, backgroundColor: 'rgba(0,0,0,0.6)', borderRadius: 12, width: 20, height: 20, justifyContent: 'center', alignItems: 'center' },
  guideBox: { backgroundColor: '#F8F9FA', padding: 20, borderRadius: 8, marginTop: 10 },
  guideTitle: { fontSize: 13, color: '#333', marginBottom: 8 },
  guideText: { fontSize: 13, color: '#777', lineHeight: 22 },
  bottomContainer: { padding: 15, paddingBottom: Platform.OS === 'ios' ? 10 : 20, backgroundColor: '#fff', borderTopWidth: 1, borderTopColor: '#F0F0F0' },
  bottomSubmitBtn: { backgroundColor: '#D4D8DB', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  bottomSubmitBtnActive: { backgroundColor: '#1B854A' },
  bottomSubmitBtnText: { color: '#FFF', fontSize: 16 }
});