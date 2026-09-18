import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Dimensions,
  FlatList,
  Image,
  NativeScrollEvent,
  NativeSyntheticEvent,
  StyleSheet,
  TouchableOpacity,
  View,
} from 'react-native';

const SIDE_PADDING = 20;
const AUTO_PLAY_INTERVAL = 4000;

type Banner = {
  id: string;
  storeId: number;
  source: any;
};

// 배너를 누르면 그 가게로 들어간다. storeId는 운영 DB의 실제 상점이다.
const BANNERS: Banner[] = [
  { id: 'damsso-bakery', storeId: 900002, source: require('../../assets/images/banners/damsso-bakery.jpg') },
  { id: 'ondam-coffee', storeId: 900001, source: require('../../assets/images/banners/ondam-coffee.jpg') },
  { id: 'oneul-banchan', storeId: 900004, source: require('../../assets/images/banners/oneul-banchan.jpg') },
];

interface EventBannerCarouselProps {
  router: any;
}

export default function EventBannerCarousel({ router }: EventBannerCarouselProps) {
  const listRef = useRef<FlatList<Banner>>(null);
  const [index, setIndex] = useState(0);
  // 손으로 넘기는 중에 자동 넘김이 끼어들면 화면이 튄다.
  const isInteractingRef = useRef(false);

  // 화면 회전·창 크기 변경(웹)에 맞춰 다시 계산한다. 한 번만 재면 웹에서 크기가 틀어진다.
  const [screenWidth, setScreenWidth] = useState(() => Dimensions.get('window').width);
  useEffect(() => {
    const subscription = Dimensions.addEventListener('change', ({ window }) =>
      setScreenWidth(window.width)
    );
    return () => subscription.remove();
  }, []);

  const pageWidth = screenWidth - SIDE_PADDING * 2;
  // 원본 배너 비율(3:1)을 유지한다.
  const pageHeight = Math.round(pageWidth / 3);

  useEffect(() => {
    if (BANNERS.length <= 1) return;

    const timer = setInterval(() => {
      if (isInteractingRef.current) return;

      setIndex((current) => {
        const next = (current + 1) % BANNERS.length;
        listRef.current?.scrollToOffset({ offset: next * pageWidth, animated: true });
        return next;
      });
    }, AUTO_PLAY_INTERVAL);

    return () => clearInterval(timer);
  }, [pageWidth]);

  const handleMomentumEnd = useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      const offset = event.nativeEvent.contentOffset.x;
      setIndex(Math.round(offset / pageWidth));
      isInteractingRef.current = false;
    },
    [pageWidth]
  );

  return (
    <View style={styles.container}>
      <FlatList
        ref={listRef}
        data={BANNERS}
        keyExtractor={(item) => item.id}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onScrollBeginDrag={() => {
          isInteractingRef.current = true;
        }}
        onMomentumScrollEnd={handleMomentumEnd}
        // 폭이 화면마다 다르므로 FlatList가 항목 위치를 직접 계산하지 못한다.
        // 넘겨주지 않으면 scrollToOffset 직후 깜빡인다.
        getItemLayout={(_, i) => ({ length: pageWidth, offset: pageWidth * i, index: i })}
        renderItem={({ item }) => (
          <TouchableOpacity
            activeOpacity={0.9}
            style={{ width: pageWidth }}
            onPress={() => router.push(`/shop/${item.storeId}` as any)}
          >
            <Image
              source={item.source}
              style={[styles.image, { width: pageWidth, height: pageHeight }]}
              resizeMode="cover"
            />
          </TouchableOpacity>
        )}
      />

      <View style={styles.dots}>
        {BANNERS.map((banner, i) => (
          <View key={banner.id} style={[styles.dot, i === index && styles.dotActive]} />
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { marginHorizontal: SIDE_PADDING, marginBottom: 25 },
  image: { borderRadius: 8, backgroundColor: '#EFEFEF' },
  dots: { flexDirection: 'row', justifyContent: 'center', marginTop: 10 },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginHorizontal: 3,
    backgroundColor: '#D9D9D9',
  },
  dotActive: { backgroundColor: '#00A859', width: 16 },
});
