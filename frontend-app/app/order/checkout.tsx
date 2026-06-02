import React, { useState, useRef, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Alert, Linking, Platform } from 'react-native';
import { Text } from '../../components/CustomText';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { WebView } from 'react-native-webview';

import { orderApi } from '../../api/order';
import { userApi } from '@/api/user';

export default function CheckoutScreen() {
  const router = useRouter();
  const webViewRef = useRef(null);
  
  // 장바구니 화면에서 넘겨준 데이터(params) 받기
  const params = useLocalSearchParams();

  const [isPaymentVisible, setIsPaymentVisible] = useState(false);
  const [currentOrderNumber, setCurrentOrderNumber] = useState('');
  const [currentOrderId, setCurrentOrderId] = useState(''); // complete.tsx 이동용 주문 ID 상태 추가

  // 화면에 보여줄 장바구니 주문 정보 상태
  const [displayOrder, setDisplayOrder] = useState({
    orderName: params.orderName ? String(params.orderName) : '장바구니 상품',
    totalPrice: Number(params.totalPrice) || 0
  });

  // 화면에 보여줄 내 정보 상태
  const [userInfo, setUserInfo] = useState({
    name: '로딩중...',
    phone: '로딩중...',
    email: ''
  });

  const [paymentData, setPaymentData] = useState({
    orderNumber: '',
    totalAmount: 0,
    orderName: '',
    customerName: '',
    customerPhone: '',
    customerEmail: ''
  });

  const uniquePaymentId = `pay_${new Date().getTime()}`; 

  // 화면이 처음 켜질 때 유저 정보 불러오기
  useEffect(() => {
    const fetchInitialData = async () => {
      try {
        const myInfo = await userApi.getMyInfo();
        setUserInfo({
          name: myInfo.name,
          phone: myInfo.phone || '010-0000-0000',
          email: myInfo.email
        });
      } catch (error) {
        console.error("내 정보 불러오기 실패", error);
      }
    };
    fetchInitialData();
  }, []);

  const handlePayment = async () => {
    try {
      const orderResponse = await orderApi.createOrder({
        paymentMethod: "CARD",
        pickupScheduledAt: "2026-05-28T13:00:00", 
        requestMessage: "봉투에 담아주세요."
      });
      
      // 스웨거 명세에 맞춘 주문 식별 데이터 저장
      setCurrentOrderNumber(orderResponse.orderNumber);
      setCurrentOrderId(String(orderResponse.orderId || '1'));

      setPaymentData({
        orderNumber: orderResponse.orderNumber,          
        totalAmount: orderResponse.totalPrice,           
        orderName: orderResponse.orderName,
        customerName: userInfo.name,        
        customerEmail: (userInfo.email && userInfo.email.includes('@') && !userInfo.email.includes('*')) 
                        ? userInfo.email 
                        : 'test@eeum.com',
        customerPhone: userInfo.phone
      });

      setIsPaymentVisible(true);
    } catch (error) {
      console.error(error);
      Alert.alert("주문 생성 실패", "장바구니 정보를 처리하는 중 문제가 발생했습니다.");
    }
  };

  // 주문 완료(complete) 페이지로 안전하게 진짜 데이터를 실어 이동시키는 헬퍼 함수
  const navigateToComplete = () => {
    router.push({
      pathname: '/order/complete',
      params: {
        orderId: currentOrderId,
        orderNumber: currentOrderNumber,
        orderName: paymentData.orderName,
        totalPrice: paymentData.totalAmount
      }
    });
  };

  const handleWebViewMessage = async (event: any) => {
    console.log("\n========================================");
    console.log("🚩 [STEP 1] 결제창에서 이벤트 수신됨!");

    const response = JSON.parse(event.nativeEvent.data);
    setIsPaymentVisible(false);

    if (response.code != null) {
      console.log("❌ [STEP 2] 포트원 결제 자체 실패:", response.message);
      Alert.alert("결제 실패", response.message);
    } else {
      console.log("✅ [STEP 2] 포트원 결제 성공! (API 검증 시작)");
      try {
        console.log("🚀 [STEP 3] 백엔드로 /payments/verify API 요청 쏘는 중...");
        await orderApi.verifyPayment(response.paymentId, currentOrderNumber);
        
        console.log("🎉 [STEP 4] 백엔드 검증 완료!");
        Alert.alert("결제 성공", "주문이 완료되었습니다!", [
          { text: "확인", onPress: () => navigateToComplete() }
        ]);
      } catch (e: any) {
        console.error("\n🚨 [STEP 4 - ERROR] 백엔드 검증 중 에러 발생!!");
        Alert.alert("결제 검증 실패", "결제는 진행되었으나 서버 검증에 실패했습니다.");
      }
    }
  };

  const handleShouldStartLoadWithRequest = (request: any) => {
    const { url } = request;

    if (url.includes('http://localhost/payment/success')) {
      setIsPaymentVisible(false);
      const urlParts = url.split('paymentId=');
      
      if (urlParts.length > 1) {
        const paymentId = urlParts[1].split('&')[0];
        console.log("✅ 결제 성공 낚아채기 완료! PaymentId:", paymentId);
        
        orderApi.verifyPayment(paymentId, currentOrderNumber)
          .then(() => {
            Alert.alert("결제 성공", "주문이 완료되었습니다!", [
              { text: "확인", onPress: () => navigateToComplete() }
            ]);
          })
          .catch((e) => {
             console.error("검증 실패:", e);
             Alert.alert("검증 실패", "서버 검증에 실패했습니다.");
          });
      }
      return false; 
    }

    if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('about:blank')) {
      return true;
    }

    if (Platform.OS === 'android' && url.startsWith('intent')) {
      const intentParts = url.split('#Intent;');
      const urlBeforeIntent = intentParts[0].replace('intent://', ''); 
      let scheme = '';
      let packageName = '';

      if (intentParts.length > 1) {
        const paramsArray = intentParts[1].split(';');
        paramsArray.forEach((param: string) => {
          if (param.startsWith('scheme=')) scheme = param.split('=')[1];
          if (param.startsWith('package=')) packageName = param.split('=')[1];
        });
      }

      if (scheme) {
        const realAppUrl = `${scheme}://${urlBeforeIntent}`;
        Linking.openURL(realAppUrl).catch(() => {
          if (packageName) {
            Linking.openURL(`market://details?id=${packageName}`);
          }
        });
      }
      return false; 
    }

    Linking.openURL(url).catch(() => {
      Alert.alert('앱 실행 실패', '해당 결제 앱이 설치되어 있지 않습니다.');
    });
    return false;
  };

  const htmlContent = `
    <!DOCTYPE html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>html, body { margin: 0; padding: 0; width: 100%; height: 100%; }</style>
        <script src="https://cdn.portone.io/v2/browser-sdk.js"></script>
      </head>
      <body>
        <script>
          window.onload = async function() {
            try {
              await PortOne.requestPayment({
                storeId: 'store-adeb0b11-deef-4d93-9e5d-b74e64e521ff',
                channelKey: 'channel-key-9cd0714c-def7-4d01-b6c5-9a4ec5557bec',
                paymentId: '${uniquePaymentId}',
                orderName: '${paymentData.orderName}', 
                totalAmount: ${paymentData.totalAmount}, 
                currency: 'CURRENCY_KRW',
                payMethod: 'CARD',
                customer: {
                  fullName: '${paymentData.customerName}',
                  phoneNumber: '${paymentData.customerPhone}',
                  email: '${paymentData.customerEmail}'
                },
                redirectUrl: 'http://localhost/payment/success'
              });
            } catch (error) {
              window.ReactNativeWebView.postMessage(JSON.stringify({ code: error.code, message: error.message }));
            }
          };
        </script>
      </body>
    </html>
  `;

  if (isPaymentVisible) {
    return (
      <SafeAreaView style={{ flex: 1 }} edges={['top', 'bottom']}>
        <TouchableOpacity style={styles.closeBtn} onPress={() => setIsPaymentVisible(false)}>
          <Ionicons name="close" size={28} color="#333" />
        </TouchableOpacity>
        <WebView
          ref={webViewRef}
          source={{ html: htmlContent, baseUrl: 'https://localhost' }}
          onMessage={handleWebViewMessage}
          javaScriptEnabled={true}
          originWhitelist={['*']} 
          onShouldStartLoadWithRequest={handleShouldStartLoadWithRequest}
          style={{ flex: 1 }}
          scalesPageToFit={false} 
          textZoom={100} 
          bounces={false} 
        />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="chevron-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>주문 / 결제</Text>
        <View style={{ width: 24 }} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false}>
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>주문 상품</Text>
          <Text style={styles.summaryText}>{displayOrder.orderName}</Text>
        </View>
        <View style={styles.divider} />

        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>배송지 정보</Text>
          <View style={styles.inputBox}>
            <Text style={styles.inputLabel}>받으시는 분</Text>
            <Text style={styles.inputValue}>{userInfo.name}님</Text>
          </View>
          <View style={styles.inputBox}>
            <Text style={styles.inputLabel}>연락처</Text>
            <Text style={styles.inputValue}>{userInfo.phone}</Text>
          </View>
        </View>
        <View style={styles.divider} />

        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>결제 상세</Text>
          <View style={styles.priceRow}>
            <Text style={styles.priceLabel}>주문 금액</Text>
            <Text style={styles.priceValue}>{displayOrder.totalPrice.toLocaleString()}원</Text>
          </View>
          <View style={[styles.priceRow, { marginTop: 15, paddingTop: 15, borderTopWidth: 1, borderColor: '#eee' }]}>
            <Text fontWeight="bold" style={styles.totalPriceLabel}>총 결제 금액</Text>
            <Text fontWeight="bold" style={styles.totalPriceValue}>{displayOrder.totalPrice.toLocaleString()}원</Text>
          </View>
        </View>
      </ScrollView>

      <View style={styles.bottomBar}>
        <TouchableOpacity style={styles.payBtn} onPress={handlePayment}>
          <Text fontWeight="bold" style={styles.payBtnText}>{displayOrder.totalPrice.toLocaleString()}원 결제하기</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#eee' },
  backButton: { padding: 5, marginLeft: -5 },
  headerTitle: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  section: { padding: 20 },
  sectionTitle: { fontSize: 18, color: '#333', marginBottom: 15 },
  summaryText: { fontSize: 15, color: '#555' },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  inputBox: { marginBottom: 15 },
  inputLabel: { fontSize: 13, color: '#888', marginBottom: 5 },
  inputValue: { fontSize: 15, color: '#333', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#eee' },
  priceRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 10 },
  priceLabel: { fontSize: 15, color: '#666' },
  priceValue: { fontSize: 15, color: '#333' },
  totalPriceLabel: { fontSize: 16, color: '#333' },
  totalPriceValue: { fontSize: 20, color: '#00A859' },
  bottomBar: { padding: 20, borderTopWidth: 1, borderTopColor: '#EEE', backgroundColor: '#fff' },
  payBtn: { backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  payBtnText: { color: '#fff', fontSize: 16 },
  closeBtn: { padding: 15, alignItems: 'flex-end', backgroundColor: '#fff' }
});