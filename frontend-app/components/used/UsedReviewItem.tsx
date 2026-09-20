import React from 'react';
import { View, StyleSheet, TouchableOpacity } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Text } from '../CustomText';
import { StarRating } from './StarRating';
import { UsedReview } from '../../api/usedReview';

interface Props {
  review: UsedReview;
  /** 주면 오른쪽에 수정/삭제 버튼이 붙는다 (내가 쓴 후기 목록). */
  onEdit?: (review: UsedReview) => void;
  onDelete?: (review: UsedReview) => void;
}

export function UsedReviewItem({ review, onEdit, onDelete }: Props) {
  return (
    <View style={styles.item}>
      <View style={styles.top}>
        <StarRating rating={review.rating} size={14} />
        <Text style={styles.date}>{review.createdAt?.substring(0, 10)}</Text>
        {(onEdit || onDelete) && (
          <View style={styles.actions}>
            {onEdit && (
              <TouchableOpacity onPress={() => onEdit(review)} hitSlop={8}>
                <Ionicons name="create-outline" size={18} color="#999" />
              </TouchableOpacity>
            )}
            {onDelete && (
              <TouchableOpacity onPress={() => onDelete(review)} hitSlop={8}>
                <Ionicons name="trash-outline" size={18} color="#999" />
              </TouchableOpacity>
            )}
          </View>
        )}
      </View>

      {/* 글이 지워지거나 숨겨지면 제목을 드러내지 않는다 */}
      <Text style={styles.product} numberOfLines={1}>
        {review.usedProductVisible ? review.usedProductTitle : '삭제된 게시글'}
      </Text>

      <Text style={styles.content}>{review.content}</Text>

      <Text style={styles.reviewer}>{review.reviewerNickname || '알 수 없음'}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  item: { paddingVertical: 18, borderBottomWidth: 1, borderBottomColor: '#F2F2F2' },
  top: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  date: { fontSize: 12, color: '#BBB' },
  actions: { flexDirection: 'row', gap: 14, marginLeft: 'auto' },
  product: { fontSize: 12, color: '#999', marginTop: 8 },
  content: { fontSize: 15, color: '#333', marginTop: 6, lineHeight: 22 },
  reviewer: { fontSize: 12, color: '#AAA', marginTop: 10 },
});
