// 네이티브 앱에서는 react-native-webview를 그대로 쓴다.
// 웹 데모 빌드는 PlatformWebView.web.tsx(iframe)가 대신 잡힌다 —
// react-native-webview에는 웹 구현이 없어 지도·결제 화면이 통째로 빈 화면이 된다.
export { WebView as default } from 'react-native-webview';
export type { WebViewMessageEvent } from 'react-native-webview';
