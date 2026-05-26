import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text } from '../../components/CustomText';

export default function TempScreen() {
  return (
    <View style={styles.container}>
      <Text>임시 화면입니다!</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fff',
  },
});
