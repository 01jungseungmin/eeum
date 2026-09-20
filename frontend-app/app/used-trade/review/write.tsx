import React, { useEffect, useState } from 'react';
import { View, StyleSheet, TextInput, TouchableOpacity, ActivityIndicator, Alert, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { Text } from '../../../components/CustomText';
import { StarRating } from '../../../components/used/StarRating';
import { usedApi } from '../../../api/used';
import { usedReviewApi } from '../../../api/usedReview';
import { getApiErrorMessage } from '../../../utils/apiError';

/**
 * 거래 후기 작성·수정.
 *
 * 작성은 usedProductId로, 수정은 reviewId로 들어온다. 서버가 자격(판매완료 +
 * 확정된 구매자)을 검사하므로 화면은 진입점만 가리고 최종 판단은 응답에 맡긴다.
 */
export default function UsedReviewWriteScreen() {
  const router = useRouter();
  const { usedProductId, reviewId, rating: initialRating, content: initialContent } =
    useLocalSearchParams();

  const productId = usedProductId ? Number(usedProductId) : null;
  const editingReviewId = reviewId ? Number(reviewId) : null;
  const isEditMode = editingReviewId !== null && !Number.isNaN(editingReviewId);

  const [rating, setRating] = useState(initialRating ? Number(initialRating) : 5);
  const [content, setContent] = useState(typeof initialContent === 'string' ? initialContent : '');
  const [productTitle, setProductTitle] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (isEditMode || productId === null) return;

    usedApi.getUsedProduct(productId)
      .then(detail => setProductTitle(detail.title))
      .catch(() => setProductTitle(''));
  }, [isEditMode, productId]);

  const handleSubmit = async () => {
    if (isSubmitting) return;
    if (!content.trim()) {
      Alert.alert('알림', '후기 내용을 입력해 주세요.');
      return;
    }

    setIsSubmitting(true);
    try {
      if (isEditMode && editingReviewId !== null) {
        await usedReviewApi.updateReview(editingReviewId, { rating, content: content.trim() });
      } else if (productId !== null) {
        await usedReviewApi.createReview(productId, { rating, content: content.trim() });
      }

      Alert.alert('완료', isEditMode ? '후기를 수정했어요.' : '후기를 남겼어요.', [
        { text: '확인', onPress: () => router.back() },
      ]);
    } catch (error) {
      // 자격이 없거나(판매완료 전, 구매자 아님) 이미 쓴 경우 서버가 사유를 준다.
      Alert.alert('후기를 저장할 수 없어요', getApiErrorMessage(error, '잠시 후 다시 시도해 주세요.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Stack.Screen options={{ headerShown: false }} />

      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.headerIcon}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>
          {isEditMode ? '후기 수정' : '거래 후기'}
        </Text>
        <TouchableOpacity onPress={handleSubmit} disabled={isSubmitting} style={styles.headerSubmit}>
          {isSubmitting
            ? <ActivityIndicator size="small" color="#00A859" />
            : <Text style={styles.headerSubmitText}>완료</Text>}
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        {!!productTitle && (
          <Text style={styles.productTitle} numberOfLines={1}>{productTitle}</Text>
        )}

        <Text style={styles.label}>거래는 어떠셨나요?</Text>
        <View style={styles.starRow}>
          <StarRating rating={rating} size={36} onChange={setRating} />
        </View>

        <Text style={styles.label}>후기</Text>
        <TextInput
          style={styles.textArea}
          value={content}
          onChangeText={setContent}
          placeholder="약속은 잘 지켜졌는지, 상품 상태는 설명과 같았는지 남겨 주세요."
          placeholderTextColor="#BBB"
          multiline
          textAlignVertical="top"
          maxLength={500}
        />
        <Text style={styles.counter}>{content.length}/500</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFF' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 8, height: 56 },
  headerIcon: { padding: 8 },
  headerTitle: { fontSize: 17, color: '#333' },
  headerSubmit: { paddingHorizontal: 12, minWidth: 56, alignItems: 'flex-end' },
  headerSubmitText: { fontSize: 16, color: '#00A859', fontWeight: 'bold' },
  content: { padding: 20 },
  productTitle: { fontSize: 14, color: '#888', marginBottom: 20 },
  label: { fontSize: 15, fontWeight: 'bold', color: '#333', marginBottom: 12 },
  starRow: { alignItems: 'center', marginBottom: 28 },
  textArea: { minHeight: 160, borderWidth: 1, borderColor: '#EEE', borderRadius: 8, padding: 14, fontSize: 15, color: '#333', backgroundColor: '#FAFAFA' },
  counter: { fontSize: 12, color: '#BBB', textAlign: 'right', marginTop: 6 },
});
