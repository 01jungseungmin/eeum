import React from 'react';
import { View, Image, StyleSheet, StyleProp, ViewStyle, ImageStyle } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

type Props = {
  uri?: string | null;
  style?: StyleProp<ViewStyle & ImageStyle>;
  iconSize?: number;
};

/**
 * 가게 썸네일. uri가 없으면 회색 판에 아이콘을 그린다.
 *
 * 예전에는 자리표시자로 https://placehold.co 이미지를 받아왔는데, 외부 호스트라
 * 차단되거나 느리면 카드에 깨진 이미지가 그대로 남는다. 예약 목록처럼 백엔드가
 * 아직 이미지를 안 내려주는 화면은 항상 그 자리표시자였다.
 */
export function StoreThumbnail({ uri, style, iconSize = 22 }: Props) {
  if (uri) {
    return <Image source={{ uri }} style={style} />;
  }

  return (
    <View style={[styles.placeholder, style]}>
      <Ionicons name="storefront-outline" size={iconSize} color="#BDBDBD" />
    </View>
  );
}

const styles = StyleSheet.create({
  placeholder: {
    backgroundColor: '#F2F2F2',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
