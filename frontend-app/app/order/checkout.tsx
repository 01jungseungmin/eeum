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
  const params = useLocalSearchParams();

  const [isPaymentVisible, setIsPaymentVisible] = useState(false);
  const [currentOrderNumber, setCurrentOrderNumber] = useState('');
  const [currentOrderId, setCurrentOrderId] = useState('');

  // 결제 수단 State (대분류)
  const [selectedPayMethod, setSelectedPayMethod] = useState('CARD');
  // 간편결제사 State (소분류)
  const [easyPayProvider, setEasyPayProvider] = useState('TOSSPAY'); 

  const [displayOrder, setDisplayOrder] = useState({
    orderName: params.orderName ? String(params.orderName) : '장바구니 상품',
    totalPrice: Number(params.totalPrice) || 0
  });

  const [userInfo, setUserInfo] = useState({
    name: '로딩중...',
    phone: '로딩중...',
    email: ''
  });

  // 포트원 동적 파라미터 세팅 세트
  const [paymentData, setPaymentData] = useState({
    orderNumber: '',
    paymentId: '',
    totalAmount: 0,
    orderName: '',
    customerName: '',
    customerPhone: '',
    customerEmail: '',
    portonePayMethod: 'CARD', 
    portoneChannelKey: '',
    easyPayProvider: ''
  });
 

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
    // 안전장치: 만약 이미 한 번 주문생성이 완료되어 주문번호가 있다면,
    // 백엔드 API를 다시 호출하지 않고 (장바구니 비어있음 에러 방지) 곧바로 결제창만 다시 열어줍니다.
    if (currentOrderNumber && selectedPayMethod !== 'ONSITE') {
      setPaymentData((prev) => ({
        ...prev,
        portonePayMethod: selectedPayMethod === 'EASY_PAY' ? 'EASY_PAY' : selectedPayMethod,
        easyPayProvider: selectedPayMethod === 'EASY_PAY' ? easyPayProvider : ''
      }));
      setIsPaymentVisible(true);
      return;
    }

    try {
      // 백엔드 명세와 동기화 (EASY_PAY일 경우 세부 프로바이더 전달)
      let backendPayMethod = selectedPayMethod;

      if (selectedPayMethod === 'EASY_PAY') {
        backendPayMethod = 'EASY_PAY'; // 🎯 TOSSPAY 대신 백엔드가 원하는 대분류 단어로 고정!
      } else if (selectedPayMethod === 'ONSITE') {
        backendPayMethod = 'CASH_ON_SITE'; // 🎯 백엔드 로그에 적힌 단어 명세와 일치시킴
      }

      const res = await orderApi.createOrder({
        paymentMethod: backendPayMethod
      });
      
      const orderResponse = res.data?.data || res.data || res;

      setCurrentOrderNumber(orderResponse.orderNumber);
      setCurrentOrderId(String(orderResponse.orderId || '1'));

      // 현장 결제 분기 처리 (포트원 웹뷰 우회)
      if (selectedPayMethod === 'ONSITE') {
        Alert.alert("주문 접수", "현장 결제로 주문이 접수되었습니다.", [
          { text: "확인", onPress: () => navigateToComplete() }
        ]);
        return; 
      }

      // 토스페이먼츠 단일 채널 및 간편결제 프로바이더 데이터 동적 주입
      setPaymentData({
        orderNumber: orderResponse.orderNumber,
        paymentId: orderResponse.paymentId,          
        totalAmount: orderResponse.totalPrice,           
        orderName: orderResponse.orderName,
        customerName: userInfo.name,        
        customerEmail: (userInfo.email && userInfo.email.includes('@') && !userInfo.email.includes('*')) 
                        ? userInfo.email 
                        : 'test@eeum.com',
        customerPhone: userInfo.phone,
        portonePayMethod: selectedPayMethod === 'EASY_PAY' ? 'EASY_PAY' : selectedPayMethod,
        portoneChannelKey: process.env.EXPO_PUBLIC_PORTONE_TOSS_CHANNEL_KEY || '', 
        easyPayProvider: selectedPayMethod === 'EASY_PAY' ? easyPayProvider : ''
      });

      setIsPaymentVisible(true);
    } catch (error) {
      console.error(error);
      Alert.alert("주문 생성 실패", "결제 준비 중 문제가 발생했습니다.");
    }
  };

  const navigateToComplete = () => {
    router.push({
      pathname: '/order/complete',
      params: {
        orderId: currentOrderId,
        orderNumber: currentOrderNumber,
        orderName: displayOrder.orderName,
        totalPrice: displayOrder.totalPrice
      }
    });
  };

  const handleWebViewMessage = async (event: any) => {
    const response = JSON.parse(event.nativeEvent.data);
    setIsPaymentVisible(false);

    if (response.code != null) {
      Alert.alert("결제 실패", response.message);
    } else {
      try {
        await orderApi.verifyPayment(response.paymentId, currentOrderNumber);
        Alert.alert("결제 성공", "주문이 완료되었습니다!", [
          { text: "확인", onPress: () => navigateToComplete() }
        ]);
      } catch (e: any) {
        Alert.alert("결제 검증 실패", "결제는 진행되었으나 서버 검증에 실패했습니다.");
      }
    }
  };

  const handleShouldStartLoadWithRequest = (request: any) => {
    const { url } = request;

    // 1. 결제 완료 성공 주소 낚아채기
    if (url.includes('http://localhost/payment/success')) {
      setIsPaymentVisible(false);
      const urlParts = url.split('paymentId=');
      if (urlParts.length > 1) {
        const paymentId = urlParts[1].split('&')[0];
        orderApi.verifyPayment(paymentId, currentOrderNumber)
          .then(() => {
            Alert.alert("결제 성공", "주문이 완료되었습니다!", [
              { text: "확인", onPress: () => navigateToComplete() }
            ]);
          })
          .catch(() => Alert.alert("검증 실패", "서버 검증에 실패했습니다."));
      }
      return false; 
    }

    // 2. 일반 웹 주소(http, https)는 웹뷰 안에서 그대로 보여주기
    if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('about:blank')) {
      return true;
    }

    // 3. 안드로이드 딥링크(Intent) 파싱 및 외부 앱 호출 로직 복원 (토스/카카오 앱 오픈 핵심)
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
        // 실제 외부 앱 스키마 주소 조립 (예: supertoss://...)
        const realAppUrl = `${scheme}://${urlBeforeIntent}`;
        Linking.openURL(realAppUrl).catch(() => {
          // 앱이 설치 안 되어 있으면 구글 플레이스토어로 이동
          if (packageName) {
            Linking.openURL(`market://details?id=${packageName}`);
          }
        });
      } else if (packageName) {
        Linking.openURL(`market://details?id=${packageName}`);
      }
      return false; 
    }

    // 4. iOS 및 기타 커스텀 스키마 외부 앱 열기 (supertoss:// 등)
    Linking.openURL(url).catch(() => {
      Alert.alert('앱 실행 실패', '결제 앱이 설치되어 있지 않거나 열 수 없습니다.');
    });
    return false;
  };

  const easyPayScript = paymentData.portonePayMethod === 'EASY_PAY' 
    ? `easyPay: { easyPayProvider: '${paymentData.easyPayProvider}' },` 
    : '';

  const htmlContent = `
    <!DOCTYPE html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <script src="https://cdn.portone.io/v2/browser-sdk.js"></script>
      </head>
      <body>
        <script>
          window.onload = async function() {
            try {
              await PortOne.requestPayment({
                storeId: '${process.env.EXPO_PUBLIC_PORTONE_STORE_ID}',
                channelKey: '${process.env.EXPO_PUBLIC_PORTONE_DEFAULT_KEY}',
                paymentId: '${paymentData.paymentId}',
                orderName: '${paymentData.orderName}', 
                totalAmount: ${paymentData.totalAmount}, 
                currency: 'CURRENCY_KRW',
                payMethod: '${paymentData.portonePayMethod}', 
                ${easyPayScript}
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
        <Text fontWeight="bold" style={styles.headerTitle}>주문/결제</Text>
        <View style={{ width: 24 }} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false}>
        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>주문자</Text>
          <View style={styles.buyerBox}>
            <Text style={styles.buyerText}>{userInfo.name}  {userInfo.phone}</Text>
          </View>
        </View>

        <View style={styles.divider} />

        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>주문 상품</Text>
          <Text style={styles.summaryText}>{displayOrder.orderName}</Text>
        </View>

        <View style={styles.divider} />

        <View style={styles.section}>
          <Text fontWeight="bold" style={styles.sectionTitle}>결제 수단</Text>
          
          <View style={styles.payMethodRow}>
            {['CARD', 'EASY_PAY', 'TRANSFER', 'ONSITE'].map((method, index) => {
              const labels = ['카드', '간편결제', '계좌이체', '현장결제'];
              const isActive = selectedPayMethod === method;
              return (
                <TouchableOpacity 
                  key={method}
                  style={[styles.payMethodBtn, isActive && styles.payMethodBtnActive]}
                  onPress={() => setSelectedPayMethod(method)}
                >
                  <Text style={[styles.payMethodText, isActive && styles.payMethodTextActive]}>
                    {labels[index]}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>

          {selectedPayMethod === 'EASY_PAY' && (
            <View style={styles.subPayMethodRow}>
              {['TOSSPAY', 'KAKAOPAY', 'NAVERPAY'].map((provider, index) => {
                const labels = ['토스페이', '카카오페이', '네이버페이'];
                const isActive = easyPayProvider === provider;
                return (
                  <TouchableOpacity 
                    key={provider}
                    style={[styles.subPayMethodBtn, isActive && styles.subPayMethodBtnActive]}
                    onPress={() => setEasyPayProvider(provider)}
                  >
                    <Text style={[styles.subPayMethodText, isActive && styles.subPayMethodTextActive]}>
                      {labels[index]}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          )}

          {selectedPayMethod === 'CARD' && (
            <View style={styles.payMethodHintBox}>
              <Text style={styles.payMethodHintText}>결제하기 버튼을 누른 후 카드를 선택해주세요.</Text>
            </View>
          )}

          {selectedPayMethod === 'ONSITE' && (
            <View style={styles.payMethodHintBox}>
              <Text style={styles.payMethodHintText}>매장에 방문하여 상품 수령 시 현장에서 결제해주세요.</Text>
            </View>
          )}
        </View>

        <View style={styles.divider} />

        <View style={styles.section}>
          <View style={styles.priceSummaryBox}>
            <View style={styles.priceRow}>
              <Text style={styles.priceLabel}>상품 금액</Text>
              <Text style={styles.priceValue}>{displayOrder.totalPrice.toLocaleString()}원</Text>
            </View>
            <View style={[styles.priceRow, { marginTop: 15, paddingTop: 15, borderTopWidth: 1, borderColor: '#eee' }]}>
              <Text fontWeight="bold" style={styles.totalPriceLabel}>총 결제</Text>
              <Text fontWeight="bold" style={styles.totalPriceValue}>{displayOrder.totalPrice.toLocaleString()}원</Text>
            </View>
          </View>
        </View>
      </ScrollView>

      <View style={styles.bottomBar}>
        <TouchableOpacity style={styles.payBtn} onPress={handlePayment}>
          <Text fontWeight="bold" style={styles.payBtnText}>
            {selectedPayMethod === 'ONSITE' ? '현장 결제로 주문하기' : `${displayOrder.totalPrice.toLocaleString()}원 결제하기`}
          </Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 15, backgroundColor: '#00A859' },
  backButton: { padding: 5, marginLeft: -5 },
  headerTitle: { fontSize: 18, fontWeight: 'bold', color: '#fff' },
  section: { padding: 20 },
  sectionTitle: { fontSize: 16, color: '#333', marginBottom: 15 },
  buyerBox: { borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 8, padding: 15 },
  buyerText: { fontSize: 15, color: '#333' },
  summaryText: { fontSize: 15, color: '#555' },
  divider: { height: 8, backgroundColor: '#F8F8F8' },
  payMethodRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 10 },
  payMethodBtn: { flex: 1, backgroundColor: '#F5F5F5', paddingVertical: 12, borderRadius: 20, alignItems: 'center', marginHorizontal: 3 },
  payMethodBtnActive: { backgroundColor: '#00A859' },
  payMethodText: { fontSize: 13, color: '#666', fontWeight: '500' },
  payMethodTextActive: { color: '#fff', fontWeight: 'bold' },
  subPayMethodRow: { flexDirection: 'row', justifyContent: 'flex-start', marginTop: 10, gap: 8 },
  subPayMethodBtn: { paddingVertical: 10, paddingHorizontal: 15, borderRadius: 8, borderWidth: 1, borderColor: '#E0E0E0' },
  subPayMethodBtnActive: { borderColor: '#00A859', backgroundColor: '#E8F5E9' },
  subPayMethodText: { fontSize: 13, color: '#666' },
  subPayMethodTextActive: { color: '#00A859', fontWeight: 'bold' },
  payMethodHintBox: { borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 8, padding: 15, marginTop: 15 },
  payMethodHintText: { color: '#888', fontSize: 14 },
  priceSummaryBox: { backgroundColor: '#F8F9FA', padding: 20, borderRadius: 12 },
  priceRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  priceLabel: { fontSize: 14, color: '#666' },
  priceValue: { fontSize: 14, color: '#333' },
  totalPriceLabel: { fontSize: 16, color: '#333' },
  totalPriceValue: { fontSize: 20, color: '#00A859' },
  bottomBar: { padding: 20, backgroundColor: '#fff', borderTopWidth: 1, borderTopColor: '#eee' },
  payBtn: { backgroundColor: '#00A859', paddingVertical: 16, borderRadius: 8, alignItems: 'center' },
  payBtnText: { color: '#fff', fontSize: 16 },
  closeBtn: { padding: 15, alignItems: 'flex-end', backgroundColor: '#fff' }
});