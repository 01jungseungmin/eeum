// 중고거래 "대략 거래 장소" 선택용 지도. 상점 지도(kakaoMapHtml)와 달리
// services 라이브러리(장소 검색·역지오코딩)가 필요해 별도 템플릿으로 둔다.
export const getKakaoLocationPickerHtml = (kakaoJsKey: string) => `
  <!DOCTYPE html>
  <html lang="ko">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <style>
      html, body { width: 100%; height: 100%; margin: 0; padding: 0; }
      #map { width: 100%; height: 100%; }
    </style>
  </head>
  <body>
    <div id="map">지도 로딩 중...</div>
    <script>
      var map;
      var geocoder;
      var places;
      var marker;

      function sendMessage(type, payload) {
        if (window.ReactNativeWebView && window.ReactNativeWebView.postMessage) {
          window.ReactNativeWebView.postMessage(JSON.stringify({ type: type, payload: payload }));
        }
      }

      function placeMarker(lat, lng) {
        var position = new kakao.maps.LatLng(lat, lng);
        if (!marker) {
          marker = new kakao.maps.Marker({ position: position });
          marker.setMap(map);
        } else {
          marker.setPosition(position);
        }
        map.panTo(position);
      }

      // 지도 탭으로 직접 찍은 핀 — 카카오 장소 ID는 없다. 이름은 RN 쪽에서 입력받는다.
      function reverseGeocode(lat, lng) {
        geocoder.coord2Address(lng, lat, function(result, status) {
          var address = '';
          if (status === kakao.maps.services.Status.OK && result[0]) {
            var road = result[0].road_address;
            var jibun = result[0].address;
            address = (road && road.address_name) || (jibun && jibun.address_name) || '';
          }
          sendMessage('PIN_SELECTED', { latitude: lat, longitude: lng, address: address });
        });
      }

      window.searchPlaces = function(keyword) {
        if (!keyword || !keyword.trim()) {
          sendMessage('SEARCH_RESULTS', []);
          return;
        }
        places.keywordSearch(keyword, function(data, status) {
          if (status !== kakao.maps.services.Status.OK) {
            sendMessage('SEARCH_RESULTS', []);
            return;
          }
          var results = data.slice(0, 15).map(function(item) {
            return {
              placeId: item.id,
              placeName: item.place_name,
              address: item.road_address_name || item.address_name || '',
              latitude: Number(item.y),
              longitude: Number(item.x)
            };
          });
          sendMessage('SEARCH_RESULTS', results);
        });
      };

      window.selectPlace = function(lat, lng) {
        placeMarker(lat, lng);
      };

      function initMap() {
        if (typeof kakao === 'undefined') return;
        kakao.maps.load(function() {
          try {
            var mapContainer = document.getElementById('map');
            var mapOption = { center: new kakao.maps.LatLng(37.548, 127.073), level: 4 };
            map = new kakao.maps.Map(mapContainer, mapOption);
            geocoder = new kakao.maps.services.Geocoder();
            places = new kakao.maps.services.Places();

            kakao.maps.event.addListener(map, 'click', function(mouseEvent) {
              var latlng = mouseEvent.latLng;
              placeMarker(latlng.getLat(), latlng.getLng());
              reverseGeocode(latlng.getLat(), latlng.getLng());
            });

            window.moveToLocation = function(lat, lng) {
              map.setCenter(new kakao.maps.LatLng(lat, lng));
            };

            sendMessage('MAP_READY', {});
          } catch (e) {
            sendMessage('MAP_ERROR', { message: e.message });
          }
        });
      }
      var script = document.createElement('script');
      script.src = 'https://dapi.kakao.com/v2/maps/sdk.js?appkey=${kakaoJsKey}&libraries=services&autoload=false';
      script.onload = initMap;
      document.head.appendChild(script);
    </script>
  </body>
  </html>
`;
