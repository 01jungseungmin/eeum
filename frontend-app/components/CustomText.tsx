import React from 'react';
import { Text as RNText, TextProps, StyleSheet } from 'react-native';

interface CustomTextProps extends TextProps {
  fontWeight?: 'normal' | 'bold'; // 굵기 선택용
}

export function Text(props: CustomTextProps) {
  const { style, fontWeight = 'normal', ...rest } = props;

  // 굵기에 따라 다른 폰트 파일을 연결해줍니다.
  const fontFamily = fontWeight === 'bold' ? 'Pretendard-Bold' : 'Pretendard-Regular';

  return (
    <RNText 
      {...rest} 
      style={[
        { fontFamily: fontFamily }, // 여기에 기본 폰트를 박아둡니다!
        style // 만약 외부에서 색상이나 크기를 지정하면 덮어씌워지게 합니다.
      ]} 
    />
  );
}