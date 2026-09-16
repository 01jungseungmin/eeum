import React, { useEffect, useRef, useState } from 'react';
import { View, StyleSheet, ActivityIndicator, TouchableOpacity } from 'react-native';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Text } from '../../components/CustomText';
import { extractRedirectFailure, verifyPaymentWithPendingOrder } from '../../utils/paymentCompletion';

/**
 * 외부 결제앱(토스·카카오페이 등)에서 eeum://payment/success 로 돌아오는 착지 화면.
 *
 * <p>PortOne redirectUrl이 이 주소를 가리킨다. 결제앱이 우리 앱을 다시 열 때
 * 앱이 살아 있으면 그냥 이 화면으로 이동하고, OS가 앱을 밀어냈으면 콜드 스타트로
 * 여기부터 시작한다. 두 경우 모두 결제 직전에 남겨둔 주문 정보로 검증을 마친다.
 *
 * <p>체크아웃 화면의 웹뷰 안에서 끝나는 결제는 여기까지 오지 않는다 — 그쪽은
 * 웹뷰가 요청을 가로채 같은 검증 함수를 직접 부른다.
 */
export default function PaymentSuccessScreen() {
  const router = useRouter();
  const params = useLocalSearchParams();
  const paymentId = params.paymentId ? String(params.paymentId) : '';
  const failureCode = params.code ? String(params.code) : '';
  const failureMessage = params.message ? String(params.message) : '';

  const [message, setMessage] = useState('결제를 확인하고 있어요...');
  const [isFailed, setIsFailed] = useState(false);

  // React 18 StrictMode와 재마운트로 두 번 뜰 수 있다. 검증은 한 번만 보낸다.
  const startedRef = useRef(false);

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;

    const run = async () => {
      // 실패·취소도 같은 주소로 돌아온다. paymentId가 실려 있어도 검증을 보내면 안 된다.
      const failure = extractRedirectFailure({ code: failureCode, message: failureMessage });
      if (failure) {
        setIsFailed(true);
        setMessage(`결제가 완료되지 않았습니다.\n${failure.message}`);
        return;
      }

      if (!paymentId) {
        setIsFailed(true);
        setMessage('결제 정보를 확인할 수 없습니다.\n주문 내역에서 결제 상태를 확인해 주세요.');
        return;
      }

      const result = await verifyPaymentWithPendingOrder(paymentId);

      if (result.status === 'success') {
        // replace — 뒤로가기로 이 중간 화면에 다시 오면 안 된다.
        router.replace({
          pathname: '/order/complete',
          params: {
            orderId: result.order.orderId,
            orderNumber: result.order.orderNumber,
            orderName: result.order.orderName,
            totalPrice: result.order.totalPrice
          }
        });
        return;
      }

      setIsFailed(true);
      setMessage(
        result.status === 'no-order'
          ? '주문 정보를 찾을 수 없습니다.\n주문 내역에서 결제 상태를 확인해 주세요.'
          : '결제는 진행되었으나 서버 검증에 실패했습니다.\n주문 내역에서 결제 상태를 확인해 주세요.'
      );
    };

    run();
  }, [paymentId, failureCode, failureMessage, router]);

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.content}>
        {!isFailed && <ActivityIndicator size="large" color="#00A859" style={styles.spinner} />}
        <Text style={styles.message}>{message}</Text>

        {isFailed && (
          <TouchableOpacity style={styles.button} onPress={() => router.replace('/(tabs)')}>
            <Text fontWeight="bold" style={styles.buttonText}>홈으로</Text>
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  content: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32 },
  spinner: { marginBottom: 20 },
  message: { fontSize: 15, color: '#333', textAlign: 'center', lineHeight: 22 },
  button: {
    marginTop: 28,
    backgroundColor: '#00A859',
    paddingVertical: 14,
    paddingHorizontal: 40,
    borderRadius: 8
  },
  buttonText: { color: '#fff', fontSize: 15 }
});
