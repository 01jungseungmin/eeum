export const getKakaoMapHtml = (kakaoJsKey: string) => `
  <!DOCTYPE html>
  <html lang="ko">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <style>
      html, body { width: 100%; height: 100%; margin: 0; padding: 0; background-color: #F8F9FA; }
      #map { width: 100%; height: 100%; }
      .shop-marker {
        background: #fff; border: 2px solid #00A859; border-radius: 25px; padding: 6px 12px;
        display: flex; align-items: center; gap: 4px;
        font-size: 13px; font-weight: bold; color: #333; box-shadow: 0 3px 6px rgba(0,0,0,0.2);
        position: relative; bottom: 25px; white-space: nowrap; cursor: pointer;
      }
      .shop-marker::after {
        content: ''; position: absolute; bottom: -7px; left: 50%; margin-left: -6px;
        border-width: 7px 6px 0; border-style: solid; border-color: #00A859 transparent transparent transparent;
      }
      .marker-icon { font-size: 14px; }
    </style>
  </head>
  <body>
    <div id="map">지도 로딩 중...</div>
    <script>
      var map;
      var currentOverlays = [];
      var userMarker;

      function sendLog(message) {
        if (window.ReactNativeWebView && window.ReactNativeWebView.postMessage) {
          window.ReactNativeWebView.postMessage(message);
        }
      }

      window.clickShop = function(shopId) {
        sendLog('CLICK_SHOP:' + shopId);
      };

      function getCategoryIcon(catId) {
        if (catId === 1) return '🍽️'; 
        if (catId === 2) return '☕'; 
        if (catId === 3) return '🍱'; 
        if (catId === 4) return '🥩'; 
        if (catId === 5) return '🥐'; 
        if (catId === 6) return '🏪'; 
        return '📍'; 
      }

      function initMap() {
        if (typeof kakao === 'undefined') return;
        kakao.maps.load(function() {
          try {
            var mapContainer = document.getElementById('map');
            var mapOption = { center: new kakao.maps.LatLng(37.548, 127.073), level: 3 };
            map = new kakao.maps.Map(mapContainer, mapOption);
            userMarker = new kakao.maps.Marker();

            kakao.maps.event.addListener(map, 'idle', function() {
              var center = map.getCenter();
              sendLog('MAP_MOVED:' + center.getLat() + ':' + center.getLng());
            });

            kakao.maps.event.addListener(map, 'click', function() {
              sendLog('MAP_CLICKED');
            });

            window.moveToLocation = function(lat, lng) {
              var moveLatLon = new kakao.maps.LatLng(lat, lng);
              userMarker.setPosition(moveLatLon);
              userMarker.setMap(map);
              map.panTo(moveLatLon);
            };

            window.renderShops = function(shopsJson) {
              var shops = JSON.parse(shopsJson);
              currentOverlays.forEach(function(overlay) { overlay.setMap(null); });
              currentOverlays = [];

              shops.forEach(function(shop) {
                var position = new kakao.maps.LatLng(shop.latitude, shop.longitude);
                var icon = getCategoryIcon(shop.categoryId);
                
                var content = 
                  '<div class="shop-marker" onclick="window.clickShop(' + shop.storeId + ')">' +
                    '<span class="marker-icon">' + icon + '</span>' + 
                    '<span>' + shop.name + '</span>' +
                  '</div>';

                var customOverlay = new kakao.maps.CustomOverlay({
                  position: position, content: content, clickable: true, yAnchor: 1
                });
                customOverlay.setMap(map);
                currentOverlays.push(customOverlay);
              });
            };
            sendLog('MAP_READY');
          } catch (e) {
            sendLog('[map error] ' + e.message);
          }
        });
      }
      var script = document.createElement('script');
      script.src = 'https://dapi.kakao.com/v2/maps/sdk.js?appkey=${kakaoJsKey}&autoload=false';
      script.onload = initMap;
      document.head.appendChild(script);
    </script>
  </body>
  </html>
`;