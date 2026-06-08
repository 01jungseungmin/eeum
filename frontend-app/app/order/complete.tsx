import React from 'react';
import { View, StyleSheet, TouchableOpacity } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

export default function OrderCompleteScreen() {
  const router = useRouter();

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text fontWeight="bold" style={styles.headerTitle}>주문완료</Text>
      </View>

      <View style={styles.content}>
        <View style={styles.successIcon}>
          <Ionicons name="checkmark-circle" size={100} color="#00A859" />
        </View>
        <Text fontWeight="bold" style={styles.completeTitle}>주문이 완료되었습니다!</Text>
        <Text style={styles.completeSubTitle}>신선하게 포장하여 보내드릴게요.</Text>

        <View style={styles.infoBox}>
          <View style={styles.infoRow}><Text style={styles.infoLabel}>주문번호</Text><Text style={styles.infoValue}>EE-20260428-001</Text></View>
          <View style={styles.infoRow}><Text style={styles.infoLabel}>결제금액</Text><Text style={styles.infoValue}>18,000원</Text></View>
          <View style={styles.infoRow}><Text style={styles.infoLabel}>결제일시</Text><Text style={styles.infoValue}>2026.04.28 15:40</Text></View>
        </View>
      </View>

      <View style={styles.footer}>
        <TouchableOpacity style={styles.outlineBtn} onPress={() => router.push('/order/[id]')}>
          <Text fontWeight="bold" style={styles.outlineBtnText}>주문 상세 보기</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.solidBtn} onPress={() => router.push('/')}>
          <Text fontWeight="bold" style={styles.solidBtnText}>홈으로</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { backgroundColor: '#00A859', paddingVertical: 15, alignItems: 'center' },
  headerTitle: { fontSize: 18, color: '#fff' },
  content: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 30 },
  successIcon: { marginBottom: 20 },
  completeTitle: { fontSize: 24, color: '#333', marginBottom: 10 },
  completeSubTitle: { fontSize: 16, color: '#888', marginBottom: 40 },
  infoBox: { width: '100%', backgroundColor: '#F8F9FA', padding: 20, borderRadius: 12 },
  infoRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 10 },
  infoLabel: { color: '#888' },
  infoValue: { color: '#333', fontWeight: '500' },
  footer: { padding: 20, gap: 10 },
  outlineBtn: { paddingVertical: 15, borderRadius: 8, borderWidth: 1, borderColor: '#00A859', alignItems: 'center' },
  outlineBtnText: { color: '#00A859' },
  solidBtn: { paddingVertical: 15, borderRadius: 8, backgroundColor: '#00A859', alignItems: 'center' },
  solidBtnText: { color: '#fff' }
});