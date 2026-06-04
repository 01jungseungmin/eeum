import React from 'react';
import { View, StyleSheet, TouchableOpacity } from 'react-native';
import { Text } from '../../components/CustomText';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

export default function PickupScreen() {
  const router = useRouter();
  const { shopId } = useLocalSearchParams();

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text fontWeight="bold" style={styles.headerTitle}>픽업 주문</Text>
        <View style={{ width: 24 }} />
      </View>

      <View style={styles.content}>
        <Text>선택한 식당 ID: {shopId}</Text>
        <Text>이곳에 픽업 시간 선택과 메뉴 요청사항 UI가 들어갑니다.</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: 20 },
  headerTitle: { fontSize: 18 },
  content: { flex: 1, justifyContent: 'center', alignItems: 'center' }
});