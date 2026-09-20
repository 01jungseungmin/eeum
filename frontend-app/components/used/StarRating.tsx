import React from 'react';
import { View, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

interface Props {
  rating: number;
  size?: number;
  /** 주면 누를 수 있는 입력용이 된다. 없으면 보여주기 전용. */
  onChange?: (rating: number) => void;
}

const STARS = [1, 2, 3, 4, 5];

export function StarRating({ rating, size = 20, onChange }: Props) {
  return (
    <View style={styles.row}>
      {STARS.map((value) => {
        const icon = value <= Math.round(rating) ? 'star' : 'star-outline';
        const color = value <= Math.round(rating) ? '#FFB800' : '#DDD';

        if (!onChange) {
          return <Ionicons key={value} name={icon} size={size} color={color} />;
        }

        return (
          <TouchableOpacity key={value} onPress={() => onChange(value)} hitSlop={6}>
            <Ionicons name={icon} size={size} color={color} />
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 4 },
});
