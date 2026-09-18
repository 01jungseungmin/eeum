import React, { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';

// 웹 데모 빌드 전용 react-native-webview 대체품이다.
// 화면들이 실제로 쓰는 표면은 source.html / onMessage / ref.injectJavaScript 셋뿐이라
// iframe 하나로 같은 계약을 세운다. 그 밖의 props는 네이티브 전용이라 받기만 하고 버린다.

export type WebViewMessageEvent = { nativeEvent: { data: string } };

type Props = {
  source: { html?: string; uri?: string; baseUrl?: string };
  onMessage?: (event: WebViewMessageEvent) => void;
  style?: any;
  [key: string]: any;
};

export type PlatformWebViewHandle = {
  injectJavaScript: (script: string) => void;
};

// iframe 안의 문서는 window.ReactNativeWebView를 모른다. 네이티브와 같은 이름으로
// 부모에게 postMessage를 넘겨줘야 kakaoMapHtml 같은 기존 HTML을 고치지 않고 재사용할 수 있다.
// public/webview-host.html — 빌드 결과물 루트에 그대로 복사된다.
const HOST_DOCUMENT = '/webview-host.html';

const BRIDGE_SCRIPT = `<script>
  window.ReactNativeWebView = {
    postMessage: function (message) { window.parent.postMessage(message, '*'); }
  };
</script>`;

function injectBridge(html: string): string {
  // 브리지는 페이지 스크립트보다 먼저 정의돼야 한다. head가 없으면 맨 앞에 붙인다.
  const headIndex = html.indexOf('<head>');
  if (headIndex === -1) return BRIDGE_SCRIPT + html;
  return html.slice(0, headIndex + 6) + BRIDGE_SCRIPT + html.slice(headIndex + 6);
}

const PlatformWebView = forwardRef<PlatformWebViewHandle, Props>(
  ({ source, onMessage, style }, ref) => {
    const iframeRef = useRef<HTMLIFrameElement | null>(null);
    // document.write가 끝나면 load가 한 번 더 뜬다. 막지 않으면 계속 다시 쓴다.
    const hasWrittenRef = useRef(false);

    useImperativeHandle(ref, () => ({
      injectJavaScript: (script: string) => {
        // srcDoc iframe은 부모와 같은 오리진이라 contentWindow 접근이 열려 있다.
        // 아직 로드 전이면 조용히 넘긴다 — 호출부가 이미 옵셔널 체이닝으로 방어하고 있다.
        try {
          (iframeRef.current?.contentWindow as any)?.eval(script);
        } catch (error) {
          console.warn('웹 데모 injectJavaScript 실패:', error);
        }
      },
    }));

    useEffect(() => {
      if (!onMessage) return;

      const handleMessage = (event: MessageEvent) => {
        // 확장 프로그램 등 다른 출처의 메시지가 섞여 들어온다. iframe이 보낸 것만 받는다.
        if (event.source !== iframeRef.current?.contentWindow) return;
        if (typeof event.data !== 'string') return;
        onMessage({ nativeEvent: { data: event.data } });
      };

      window.addEventListener('message', handleMessage);
      return () => window.removeEventListener('message', handleMessage);
    }, [onMessage]);

    const iframeStyle: React.CSSProperties = {
      border: 'none',
      width: '100%',
      height: '100%',
      flex: 1,
      ...(style ?? {}),
    };

    // 같은 출처의 빈 문서를 띄운 뒤 내용을 써 넣는다. srcdoc을 쓰면 문서 URL이
    // about:srcdoc이라 그 안의 카카오 지도 SDK가 location.protocol을 http로 읽고,
    // HTTPS 배포본에서 후속 스크립트가 mixed content로 차단돼 지도가 뜨지 않는다.
    // document.write는 문서 URL을 바꾸지 않으므로 https가 유지된다.
    const handleLoad = () => {
      if (source.uri || hasWrittenRef.current) return;

      const doc = iframeRef.current?.contentDocument;
      if (!doc) return;

      hasWrittenRef.current = true;
      doc.open();
      doc.write(injectBridge(source.html ?? ''));
      doc.close();
    };

    if (source.uri) {
      return <iframe ref={iframeRef} src={source.uri} style={iframeStyle} />;
    }

    return (
      <iframe
        ref={iframeRef}
        src={HOST_DOCUMENT}
        onLoad={handleLoad}
        style={iframeStyle}
      />
    );
  }
);

PlatformWebView.displayName = 'PlatformWebView';

export default PlatformWebView;
